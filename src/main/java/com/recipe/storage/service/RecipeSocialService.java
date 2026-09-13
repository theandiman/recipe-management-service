package com.recipe.storage.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import com.recipe.shared.model.Recipe;
import com.recipe.storage.dto.PagedRecipeResponse;
import com.recipe.storage.dto.RecipeResponse;
import com.recipe.storage.mapper.RecipeMapper;
import com.recipe.storage.service.RecipeAuthorService.AuthorInfo;
import com.recipe.storage.util.PaginationUtils;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service responsible for recipe social features:
 * likes, bookmarks/saved recipes, and follower feed generation.
 */
@Slf4j
@Service
@NoArgsConstructor
@AllArgsConstructor
public class RecipeSocialService {

  @Autowired(required = false)
  private Firestore firestore;

  @Autowired(required = false)
  private FollowService followService;

  @Autowired(required = false)
  private NotificationService notificationService;

  @Autowired
  private RecipeAuthorService recipeAuthorService;

  @Autowired
  private RecipeMapper recipeMapper;

  @Value("${firestore.collection.recipes:recipes}")
  private String recipesCollection = "recipes";

  @Value("${firestore.collection.saved-recipes:savedRecipes}")
  private String savedRecipesCollection = "savedRecipes";

  @Value("${firestore.collection.likes:likes}")
  private String likesCollection = "likes";

  /**
   * Save (bookmark) a recipe for a user.
   *
   * @param recipeId The recipe ID to save
   * @param userId   The Firebase user ID
   * @throws ResponseStatusException 404 if the recipe does not exist or is private and not owned
   *                                 by {@code userId}, 503 if Firestore is unavailable
   */
  public void saveRecipeForUser(String recipeId, String userId) {
    if (firestore == null) {
      log.warn("Firestore not configured - cannot save recipe");
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Database not configured");
    }

    try {
      DocumentReference recipeDocRef = firestore.collection(recipesCollection).document(recipeId);
      DocumentSnapshot recipeDoc = recipeDocRef.get().get();

      if (!recipeDoc.exists()) {
        log.warn("Attempt to save non-existent recipe {}", recipeId);
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found");
      }

      Recipe recipe = recipeDoc.toObject(Recipe.class);
      if (recipe != null && !recipe.isPublicRecipe() && !userId.equals(recipe.getUserId())) {
        log.warn("User {} attempted to save private recipe {} owned by {}",
            userId, recipeId, recipe.getUserId());
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found");
      }

      DocumentReference savedDocRef = firestore
          .collection(savedRecipesCollection)
          .document(userId)
          .collection("recipes")
          .document(recipeId);

      firestore.runTransaction(transaction -> {
        DocumentSnapshot existingSavedDoc = transaction.get(savedDocRef).get();
        if (!existingSavedDoc.exists()) {
          Map<String, Object> data = new HashMap<>();
          data.put("savedAt", Timestamp.now());
          transaction.set(savedDocRef, data);
        }
        return null;
      }).get();

      log.info("Recipe {} saved by user {}", recipeId, userId);
    } catch (ResponseStatusException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Error saving recipe bookmark for recipe {} user {}", recipeId, userId, e);
      throw new RuntimeException("Failed to save recipe", e);
    } catch (ExecutionException e) {
      log.error("Error saving recipe bookmark for recipe {} user {}", recipeId, userId, e);
      throw new RuntimeException("Failed to save recipe", e);
    }
  }

  /**
   * Unsave (remove bookmark) a recipe for a user.
   *
   * @param recipeId The recipe ID to unsave
   * @param userId   The Firebase user ID
   * @throws ResponseStatusException 503 if Firestore is unavailable
   */
  public void unsaveRecipeForUser(String recipeId, String userId) {
    if (firestore == null) {
      log.warn("Firestore not configured - cannot unsave recipe");
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Database not configured");
    }

    try {
      DocumentReference savedDocRef = firestore
          .collection(savedRecipesCollection)
          .document(userId)
          .collection("recipes")
          .document(recipeId);

      savedDocRef.delete().get();
      log.info("Recipe {} unsaved by user {}", recipeId, userId);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Error unsaving recipe bookmark for recipe {} user {}", recipeId, userId, e);
      throw new RuntimeException("Failed to unsave recipe", e);
    } catch (ExecutionException e) {
      log.error("Error unsaving recipe bookmark for recipe {} user {}", recipeId, userId, e);
      throw new RuntimeException("Failed to unsave recipe", e);
    }
  }

  /**
   * Like a recipe for a user.
   *
   * @param recipeId The recipe ID to like
   * @param userId   The Firebase user ID
   */
  public void likeRecipe(String recipeId, String userId) {
    if (firestore == null) {
      log.warn("Firestore not configured - cannot like recipe");
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Database not configured");
    }

    try {
      DocumentReference recipeDocRef = firestore.collection(recipesCollection).document(recipeId);
      DocumentSnapshot recipeDoc = recipeDocRef.get().get();

      if (!recipeDoc.exists()) {
        log.warn("Attempt to like non-existent recipe {}", recipeId);
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found");
      }

      DocumentReference likeDocRef = firestore
          .collection(likesCollection)
          .document(recipeId)
          .collection("users")
          .document(userId);

      Boolean isNewLike = firestore.runTransaction(transaction -> {
        DocumentSnapshot existingLike = transaction.get(likeDocRef).get();
        if (!existingLike.exists()) {
          transaction.set(likeDocRef,
              Map.of("likedAt", FieldValue.serverTimestamp()));
          transaction.update(recipeDocRef, "likeCount", FieldValue.increment(1));
          return true;
        }
        return false;
      }).get();

      if (Boolean.TRUE.equals(isNewLike) && notificationService != null) {
        Recipe recipe = recipeDoc.toObject(Recipe.class);
        if (recipe != null && recipe.getUserId() != null && !recipe.getUserId().equals(userId)) {
          try {
            String actorName = recipeAuthorService.resolveDisplayName(userId);
            notificationService.createNotification(
                recipe.getUserId(),
                userId,
                actorName,
                "RECIPE_LIKE",
                recipeId,
                recipe.getRecipeName(),
                null
            );
          } catch (Exception e) {
            log.warn("Failed to create like notification: {}", e.getMessage());
          }
        }
      }

      log.info("Recipe {} liked by user {}", recipeId, userId);
    } catch (ResponseStatusException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Error liking recipe {} for user {}", recipeId, userId, e);
      throw new RuntimeException("Failed to like recipe", e);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof ResponseStatusException rse) {
        throw rse;
      }
      log.error("Error liking recipe {} for user {}", recipeId, userId, e);
      throw new RuntimeException("Failed to like recipe", e);
    }
  }

  /**
   * Unlike a recipe for a user.
   *
   * @param recipeId The recipe ID to unlike
   * @param userId   The Firebase user ID
   */
  public void unlikeRecipe(String recipeId, String userId) {
    if (firestore == null) {
      log.warn("Firestore not configured - cannot unlike recipe");
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Database not configured");
    }

    try {
      DocumentReference recipeDocRef = firestore.collection(recipesCollection).document(recipeId);
      DocumentReference likeDocRef = firestore
          .collection(likesCollection)
          .document(recipeId)
          .collection("users")
          .document(userId);

      firestore.runTransaction(transaction -> {
        DocumentSnapshot existingLike = transaction.get(likeDocRef).get();
        if (existingLike.exists()) {
          transaction.delete(likeDocRef);
          transaction.update(recipeDocRef, "likeCount", FieldValue.increment(-1));
        }
        return null;
      }).get();

      log.info("Recipe {} unliked by user {}", recipeId, userId);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Error unliking recipe {} for user {}", recipeId, userId, e);
      throw new RuntimeException("Failed to unlike recipe", e);
    } catch (ExecutionException e) {
      log.error("Error unliking recipe {} for user {}", recipeId, userId, e);
      throw new RuntimeException("Failed to unlike recipe", e);
    }
  }

  /**
   * Get paginated saved recipes for a user.
   *
   * @param userId The user ID
   * @param pageToken Opaque cursor token
   * @param size Page size
   * @return PagedRecipeResponse
   */
  public PagedRecipeResponse getSavedRecipes(String userId, String pageToken, int size) {
    if (size < 1) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page size must be at least 1");
    }
    if (size > 100) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page size must not exceed 100");
    }

    Timestamp cursor = null;
    if (pageToken != null && !pageToken.isEmpty()) {
      cursor = PaginationUtils.decodePageToken(pageToken);
    }

    if (firestore == null) {
      log.warn("Firestore not configured - returning empty paged response");
      return PagedRecipeResponse.builder()
          .recipes(new ArrayList<>())
          .size(size)
          .totalCount(0)
          .nextPageToken(null)
          .build();
    }

    try {
      var savedRef = firestore
          .collection(savedRecipesCollection)
          .document(userId)
          .collection("recipes");

      final long totalCount = savedRef.count().get().get().getCount();

      Query pagedQuery = savedRef.orderBy("savedAt", Query.Direction.DESCENDING);
      if (cursor != null) {
        pagedQuery = pagedQuery.startAfter(cursor);
      }
      pagedQuery = pagedQuery.limit(size);

      QuerySnapshot querySnapshot = pagedQuery.get().get();

      List<RecipeResponse> recipes = new ArrayList<>();
      List<DocumentReference> recipeRefs = new ArrayList<>();
      for (DocumentSnapshot savedDoc : querySnapshot.getDocuments()) {
        recipeRefs.add(firestore.collection(recipesCollection).document(savedDoc.getId()));
      }

      if (!recipeRefs.isEmpty()) {
        List<DocumentSnapshot> recipeDocs = firestore
            .getAll(recipeRefs.toArray(new DocumentReference[0]))
            .get();

        for (DocumentSnapshot recipeDoc : recipeDocs) {
          if (recipeDoc.exists()) {
            Recipe recipe = recipeDoc.toObject(Recipe.class);
            if (recipe != null
                && (recipe.isPublicRecipe() || userId.equals(recipe.getUserId()))) {
              RecipeResponse response = recipeMapper.mapToResponse(recipe);
              response.setSavedByCurrentUser(true);
              response.setLikeCount(extractLikeCount(recipeDoc));
              recipes.add(response);
            }
          }
        }

        if (!recipes.isEmpty()) {
          List<DocumentReference> likeRefs = new ArrayList<>();
          for (RecipeResponse r : recipes) {
            likeRefs.add(firestore
                .collection(likesCollection)
                .document(r.getId())
                .collection("users")
                .document(userId));
          }
          List<DocumentSnapshot> likeDocs = firestore
              .getAll(likeRefs.toArray(new DocumentReference[0]))
              .get();
          for (int i = 0; i < likeDocs.size(); i++) {
            recipes.get(i).setLikedByCurrentUser(likeDocs.get(i).exists());
          }
        }
      }

      String nextToken = PaginationUtils.encodeNextPageTokenFromField(querySnapshot, "savedAt");
      log.info("Found {} saved recipes for user {} (size={}, total={})",
          recipes.size(), userId, size, totalCount);
      return PagedRecipeResponse.builder()
          .recipes(recipes)
          .size(size)
          .totalCount(totalCount)
          .nextPageToken(nextToken)
          .build();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Error fetching saved recipes for user {}", userId, e);
      throw new RuntimeException("Failed to fetch saved recipes", e);
    } catch (ExecutionException e) {
      log.error("Error fetching saved recipes for user {}", userId, e);
      throw new RuntimeException("Failed to fetch saved recipes", e);
    }
  }

  /**
   * Get follower feed for a user.
   *
   * @param userId The user ID
   * @param pageToken Opaque cursor token
   * @param size Page size
   * @return PagedRecipeResponse
   */
  public PagedRecipeResponse getFeed(String userId, String pageToken, int size) {
    if (size < 1) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page size must be at least 1");
    }
    if (size > 100) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page size must not exceed 100");
    }

    Timestamp cursor = null;
    if (pageToken != null && !pageToken.isEmpty()) {
      cursor = PaginationUtils.decodePageToken(pageToken);
    }

    List<String> followingIds = followService != null
        ? followService.getFollowingIds(userId)
        : List.of();

    if (followingIds.isEmpty()) {
      log.info("Feed for user {}: following no one, returning empty feed", userId);
      return emptyFeedResponse(size);
    }

    if (firestore == null) {
      log.warn("Firestore not configured - returning empty feed");
      return emptyFeedResponse(size);
    }

    List<List<String>> batches = PaginationUtils.partitionList(followingIds, 30);
    try {
      List<Recipe> allRecipes = new ArrayList<>();
      Map<String, Long> likeCountByRecipeId = new HashMap<>();
      for (List<String> batch : batches) {
        Query query = firestore.collection(recipesCollection)
            .whereIn("userId", batch)
            .whereEqualTo("isPublic", true)
            .orderBy("createdAt", Query.Direction.DESCENDING);
        if (cursor != null) {
          query = query.startAfter(cursor);
        }
        query = query.limit(size + 1);
        query.get().get().getDocuments()
            .forEach(doc -> {
              allRecipes.add(doc.toObject(Recipe.class));
              Long likeCount = doc.getLong("likeCount");
              if (likeCount != null) {
                likeCountByRecipeId.put(doc.getId(), likeCount);
              }
            });
      }

      allRecipes.sort((a, b) -> {
        if (b.getCreatedAt() == null) {
          return -1;
        }
        if (a.getCreatedAt() == null) {
          return 1;
        }
        return b.getCreatedAt().compareTo(a.getCreatedAt());
      });

      boolean hasNextPage = allRecipes.size() > size;
      List<Recipe> page = allRecipes.subList(0, Math.min(size, allRecipes.size()));

      List<RecipeResponse> recipes = new ArrayList<>();
      Map<String, AuthorInfo> authorCache = new HashMap<>();
      for (Recipe recipe : page) {
        RecipeResponse response = recipeMapper.mapToResponse(recipe);
        response.setLikeCount(
            likeCountByRecipeId.getOrDefault(recipe.getId(), 0L).intValue());
        String uid = recipe.getUserId();
        if (uid != null) {
          AuthorInfo authorInfo = authorCache.computeIfAbsent(uid,
              recipeAuthorService::resolveAuthorInfo);
          response.setAuthorDisplayName(authorInfo.displayName());
          response.setAuthorAvatarUrl(authorInfo.avatarUrl());
        }
        recipes.add(response);
      }

      populateLikeAndSaveStatuses(recipes, userId);

      String nextPageToken = null;
      if (hasNextPage && !page.isEmpty()) {
        java.time.Instant lastCreatedAt = page.get(page.size() - 1).getCreatedAt();
        if (lastCreatedAt != null) {
          String cursorStr = lastCreatedAt.getEpochSecond() + "," + lastCreatedAt.getNano();
          nextPageToken = Base64.getUrlEncoder().withoutPadding()
              .encodeToString(cursorStr.getBytes(StandardCharsets.UTF_8));
        }
      }

      log.info("Feed for user {}: {} recipes returned", userId, recipes.size());
      return PagedRecipeResponse.builder()
          .recipes(recipes)
          .size(size)
          .totalCount(0)
          .nextPageToken(nextPageToken)
          .build();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Interrupted while fetching feed for user {}", userId, e);
      throw new RuntimeException("Failed to fetch feed", e);
    } catch (ExecutionException e) {
      log.error("Error fetching feed for user {}", userId, e);
      throw new RuntimeException("Failed to fetch feed", e);
    }
  }

  /**
   * Check whether a specific recipe is liked by a user.
   */
  public boolean isRecipeLikedByUser(String recipeId, String userId) {
    if (firestore == null || recipeId == null || userId == null) {
      return false;
    }
    try {
      DocumentSnapshot doc = firestore
          .collection(likesCollection)
          .document(recipeId)
          .collection("users")
          .document(userId)
          .get().get();
      return doc != null && doc.exists();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Interrupted while checking like status for recipe {} user {}: {}",
          recipeId, userId, e.getMessage());
      return false;
    } catch (Exception e) {
      log.warn("Failed to check like status for recipe {} user {}: {}",
          recipeId, userId, e.getMessage());
      return false;
    }
  }

  /**
   * Check whether a specific recipe is saved (bookmarked) by a user.
   */
  public boolean isRecipeSavedByUser(String recipeId, String userId) {
    if (firestore == null || recipeId == null || userId == null) {
      return false;
    }
    try {
      DocumentSnapshot doc = firestore
          .collection(savedRecipesCollection)
          .document(userId)
          .collection("recipes")
          .document(recipeId)
          .get().get();
      return doc != null && doc.exists();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Interrupted while checking saved status for recipe {} user {}: {}",
          recipeId, userId, e.getMessage());
      return false;
    } catch (Exception e) {
      log.warn("Failed to check saved status for recipe {} user {}: {}",
          recipeId, userId, e.getMessage());
      return false;
    }
  }

  /**
   * Extract like count from snapshot.
   */
  public int extractLikeCount(DocumentSnapshot doc) {
    if (doc == null) {
      return 0;
    }
    Long count = doc.getLong("likeCount");
    return count != null ? count.intValue() : 0;
  }

  /**
   * Batch-check and populate liked and saved statuses for a list of recipes.
   *
   * @param recipes The list of recipe responses to populate
   * @param userId The Firebase UID of the current user
   */
  public void populateLikeAndSaveStatuses(List<RecipeResponse> recipes, String userId) {
    if (firestore == null || userId == null || recipes == null || recipes.isEmpty()) {
      return;
    }
    try {
      List<DocumentReference> likeRefs = new ArrayList<>(recipes.size());
      List<DocumentReference> saveRefs = new ArrayList<>(recipes.size());
      for (RecipeResponse r : recipes) {
        likeRefs.add(firestore
            .collection(likesCollection)
            .document(r.getId())
            .collection("users")
            .document(userId));
        saveRefs.add(firestore
            .collection(savedRecipesCollection)
            .document(userId)
            .collection("recipes")
            .document(r.getId()));
      }

      List<DocumentSnapshot> likeDocs = firestore
          .getAll(likeRefs.toArray(new DocumentReference[0]))
          .get();
      List<DocumentSnapshot> saveDocs = firestore
          .getAll(saveRefs.toArray(new DocumentReference[0]))
          .get();

      for (int i = 0; i < recipes.size(); i++) {
        recipes.get(i).setLikedByCurrentUser(likeDocs.get(i).exists());
        recipes.get(i).setSavedByCurrentUser(saveDocs.get(i).exists());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Interrupted while batch-checking like/save statuses for user {}: {}",
          userId, e.getMessage());
    } catch (Exception e) {
      log.warn("Failed to batch-check like/save statuses for user {}: {}",
          userId, e.getMessage());
    }
  }

  private PagedRecipeResponse emptyFeedResponse(int size) {
    return PagedRecipeResponse.builder()
        .recipes(new ArrayList<>())
        .size(size)
        .totalCount(0)
        .nextPageToken(null)
        .build();
  }
}
