package com.recipe.storage.service;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import com.recipe.shared.model.Recipe;
import com.recipe.storage.dto.CommentResponse;
import com.recipe.storage.dto.CreateCommentRequest;
import com.recipe.storage.dto.PagedCommentResponse;
import com.recipe.storage.dto.UpdateCommentRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service for recipe comments and discussions.
 */
@Slf4j
@Service
public class CommentService {

  @Autowired(required = false)
  private Firestore firestore;

  @Autowired(required = false)
  private FirebaseAuth firebaseAuth;

  @Autowired(required = false)
  private NotificationService notificationService;

  @Value("${firestore.collection.recipes:recipes}")
  private String recipesCollection;

  @Value("${firestore.collection.comments:comments}")
  private String commentsCollection;

  private final ConcurrentHashMap<String, Map<String, Object>> mockComments =
      new ConcurrentHashMap<>();

  /**
   * Create comment.
   */
  public CommentResponse createComment(String recipeId, CreateCommentRequest request,
      String userId) {
    if (firestore == null) {
      return saveToMockComments(recipeId, request, userId);
    }

    try {
      DocumentReference recipeRef = firestore.collection(recipesCollection).document(recipeId);
      DocumentSnapshot recipeDoc = recipeRef.get().get();
      if (!recipeDoc.exists()) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found");
      }

      String commentId = UUID.randomUUID().toString();
      Instant now = Instant.now();
      String authorName = resolveDisplayName(userId);

      Map<String, Object> data = new HashMap<>();
      data.put("id", commentId);
      data.put("recipeId", recipeId);
      data.put("userId", userId);
      data.put("authorName", authorName);
      data.put("content", request.getContent());
      data.put("parentId", request.getParentId());
      data.put("likeCount", 0);
      data.put("createdAt", now.toString());
      data.put("updatedAt", now.toString());

      firestore.collection(commentsCollection).document(commentId).set(data).get();

      Recipe recipe = recipeDoc.toObject(Recipe.class);
      if (notificationService != null && recipe != null && recipe.getUserId() != null
          && !recipe.getUserId().equals(userId)) {
        try {
          notificationService.createNotification(
              recipe.getUserId(),
              userId,
              authorName,
              "RECIPE_COMMENT",
              recipeId,
              recipe.getRecipeName(),
              request.getContent()
          );
        } catch (Exception e) {
          log.warn("Failed to create comment notification: {}", e.getMessage());
        }
      }

      return mapToResponse(data);
    } catch (InterruptedException | ExecutionException e) {
      log.error("Error creating comment for recipe {}", recipeId, e);
      throw new RuntimeException("Failed to create comment", e);
    }
  }

  /**
   * Get comments.
   */
  public PagedCommentResponse getComments(String recipeId, int page, int size) {
    if (firestore == null) {
      return getFromMockComments(recipeId);
    }

    try {
      Query query = firestore.collection(commentsCollection).whereEqualTo("recipeId", recipeId);
      QuerySnapshot snapshot = query.get().get();

      Map<String, CommentResponse> topLevel = new HashMap<>();
      List<CommentResponse> allReplies = new ArrayList<>();

      for (DocumentSnapshot doc : snapshot.getDocuments()) {
        Map<String, Object> data = doc.getData();
        if (data == null) {
          continue;
        }
        CommentResponse c = mapToResponse(data);

        if (c.getParentId() == null || c.getParentId().isBlank()) {
          c.setReplies(new ArrayList<>());
          topLevel.put(c.getId(), c);
        } else {
          allReplies.add(c);
        }
      }

      for (CommentResponse reply : allReplies) {
        CommentResponse parent = topLevel.get(reply.getParentId());
        if (parent != null) {
          if (parent.getReplies() == null) {
            parent.setReplies(new ArrayList<>());
          }
          parent.getReplies().add(reply);
        }
      }

      List<CommentResponse> resultList = new ArrayList<>(topLevel.values());
      resultList.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));

      int fromIndex = Math.min(page * size, resultList.size());
      int toIndex = Math.min(fromIndex + size, resultList.size());
      List<CommentResponse> paged = resultList.subList(fromIndex, toIndex);

      return PagedCommentResponse.builder()
          .totalComments(snapshot.size())
          .comments(paged)
          .hasMore(toIndex < resultList.size())
          .build();
    } catch (InterruptedException | ExecutionException e) {
      log.error("Error fetching comments for recipe {}", recipeId, e);
      throw new RuntimeException("Failed to fetch comments", e);
    }
  }

  /**
   * Update comment.
   */
  public CommentResponse updateComment(String commentId, UpdateCommentRequest request,
      String userId) {
    if (firestore == null) {
      Map<String, Object> data = mockComments.get(commentId);
      if (data == null) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found");
      }
      if (!userId.equals(data.get("userId"))) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
      }
      data.put("content", request.getContent());
      data.put("updatedAt", Instant.now().toString());
      return mapToResponse(data);
    }

    try {
      DocumentReference docRef = firestore.collection(commentsCollection).document(commentId);
      DocumentSnapshot doc = docRef.get().get();
      if (!doc.exists()) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found");
      }
      if (!userId.equals(doc.getString("userId"))) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
      }

      Map<String, Object> update = new HashMap<>();
      update.put("content", request.getContent());
      update.put("updatedAt", Instant.now().toString());
      docRef.update(update).get();

      Map<String, Object> full = new HashMap<>(doc.getData());
      full.putAll(update);
      return mapToResponse(full);
    } catch (InterruptedException | ExecutionException e) {
      log.error("Error updating comment {}", commentId, e);
      throw new RuntimeException("Failed to update comment", e);
    }
  }

  /**
   * Delete comment.
   */
  public void deleteComment(String commentId, String userId) {
    if (firestore == null) {
      mockComments.remove(commentId);
      return;
    }

    try {
      DocumentReference docRef = firestore.collection(commentsCollection).document(commentId);
      DocumentSnapshot doc = docRef.get().get();
      if (!doc.exists()) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found");
      }
      String commentOwner = doc.getString("userId");
      String recipeId = doc.getString("recipeId");

      boolean isCommentOwner = userId.equals(commentOwner);
      boolean isRecipeOwner = false;
      if (recipeId != null) {
        DocumentSnapshot recipeDoc =
            firestore.collection(recipesCollection).document(recipeId).get().get();
        if (recipeDoc.exists() && userId.equals(recipeDoc.getString("userId"))) {
          isRecipeOwner = true;
        }
      }

      if (!isCommentOwner && !isRecipeOwner) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
      }

      docRef.delete().get();
    } catch (InterruptedException | ExecutionException e) {
      log.error("Error deleting comment {}", commentId, e);
      throw new RuntimeException("Failed to delete comment", e);
    }
  }

  private CommentResponse mapToResponse(Map<String, Object> data) {
    Object countObj = data.get("likeCount");
    int count = countObj instanceof Long ? ((Long) countObj).intValue() : (Integer) countObj;

    return CommentResponse.builder()
        .id((String) data.get("id"))
        .recipeId((String) data.get("recipeId"))
        .userId((String) data.get("userId"))
        .authorName((String) data.get("authorName"))
        .authorAvatarUrl((String) data.get("authorAvatarUrl"))
        .content((String) data.get("content"))
        .parentId((String) data.get("parentId"))
        .likeCount(count)
        .createdAt((String) data.get("createdAt"))
        .updatedAt((String) data.get("updatedAt"))
        .build();
  }

  private CommentResponse saveToMockComments(String recipeId, CreateCommentRequest request,
      String userId) {
    String commentId = UUID.randomUUID().toString();
    Map<String, Object> data = new HashMap<>();
    data.put("id", commentId);
    data.put("recipeId", recipeId);
    data.put("userId", userId);
    data.put("authorName", "Chef User");
    data.put("content", request.getContent());
    data.put("parentId", request.getParentId());
    data.put("likeCount", 0);
    data.put("createdAt", Instant.now().toString());
    data.put("updatedAt", Instant.now().toString());

    mockComments.put(commentId, data);
    return mapToResponse(data);
  }

  private PagedCommentResponse getFromMockComments(String recipeId) {
    List<CommentResponse> list = new ArrayList<>();
    mockComments.values().forEach(data -> {
      if (recipeId.equals(data.get("recipeId"))) {
        list.add(mapToResponse(data));
      }
    });
    return PagedCommentResponse.builder()
        .totalComments(list.size())
        .comments(list)
        .hasMore(false)
        .build();
  }

  private String resolveDisplayName(String uid) {
    if (firebaseAuth != null) {
      try {
        UserRecord record = firebaseAuth.getUser(uid);
        if (record.getDisplayName() != null && !record.getDisplayName().isBlank()) {
          return record.getDisplayName();
        }
      } catch (Exception e) {
        // Ignore
      }
    }
    return "Chef " + uid.substring(0, Math.min(5, uid.length()));
  }
}
