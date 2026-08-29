package com.recipe.storage.service;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.recipe.shared.model.Recipe;
import com.recipe.storage.dto.CreateRatingRequest;
import com.recipe.storage.dto.PagedRatingResponse;
import com.recipe.storage.dto.RatingDistribution;
import com.recipe.storage.dto.RatingResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service for managing recipe ratings and reviews.
 */
@Slf4j
@Service
public class RatingService {

  @Autowired(required = false)
  private Firestore firestore;

  @Autowired(required = false)
  private FirebaseAuth firebaseAuth;

  @Autowired(required = false)
  private NotificationService notificationService;

  @Value("${firestore.collection.recipes:recipes}")
  private String recipesCollection;

  @Value("${firestore.collection.ratings:ratings}")
  private String ratingsCollection;

  private final ConcurrentHashMap<String, Map<String, Object>> mockRatings =
      new ConcurrentHashMap<>();

  /**
   * Submit or update rating.
   */
  public RatingResponse saveRating(String recipeId, CreateRatingRequest request, String userId) {
    if (firestore == null) {
      return saveToMockRatings(recipeId, request, userId);
    }

    try {
      DocumentReference recipeRef = firestore.collection(recipesCollection).document(recipeId);
      DocumentSnapshot recipeDoc = recipeRef.get().get();
      if (!recipeDoc.exists()) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found");
      }
      Recipe recipe = recipeDoc.toObject(Recipe.class);
      if (recipe == null) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found");
      }

      String ratingDocId = recipeId + "_" + userId;
      DocumentReference ratingRef =
          firestore.collection(ratingsCollection).document(ratingDocId);

      Instant now = Instant.now();
      String authorName = resolveDisplayName(userId);

      Map<String, Object> data = new HashMap<>();
      data.put("id", ratingDocId);
      data.put("recipeId", recipeId);
      data.put("userId", userId);
      data.put("authorName", authorName);
      data.put("score", request.getScore());
      data.put("reviewText", request.getReviewText());
      data.put("updatedAt", now.toString());

      DocumentSnapshot ratingDoc = ratingRef.get().get();
      if (!ratingDoc.exists()) {
        data.put("createdAt", now.toString());
      } else {
        data.put("createdAt", ratingDoc.getString("createdAt"));
      }

      ratingRef.set(data).get();
      recalculateRecipeRating(recipeId);

      if (notificationService != null && recipe.getUserId() != null
          && !recipe.getUserId().equals(userId)) {
        try {
          String snippet = request.getScore() + "⭐: "
              + (request.getReviewText() != null ? request.getReviewText() : "");
          notificationService.createNotification(
              recipe.getUserId(),
              userId,
              authorName,
              "RECIPE_RATING",
              recipeId,
              recipe.getRecipeName(),
              snippet
          );
        } catch (Exception e) {
          log.warn("Failed to create rating notification: {}", e.getMessage());
        }
      }

      return mapToResponse(data);
    } catch (InterruptedException | ExecutionException e) {
      log.error("Error saving rating for recipe {}", recipeId, e);
      throw new RuntimeException("Failed to save rating", e);
    }
  }

  /**
   * Fetch recipe ratings.
   */
  public PagedRatingResponse getRatings(String recipeId, int page, int size, String sort) {
    if (firestore == null) {
      return getFromMockRatings(recipeId);
    }

    try {
      Query query = firestore.collection(ratingsCollection).whereEqualTo("recipeId", recipeId);
      QuerySnapshot snapshot = query.get().get();

      List<RatingResponse> allRatings = new ArrayList<>();
      RatingDistribution distribution = new RatingDistribution();

      for (DocumentSnapshot doc : snapshot.getDocuments()) {
        Map<String, Object> data = doc.getData();
        if (data == null) {
          continue;
        }
        RatingResponse r = mapToResponse(data);
        allRatings.add(r);

        int score = r.getScore() != null ? r.getScore() : 5;
        switch (score) {
          case 1 -> distribution.setStar1(distribution.getStar1() + 1);
          case 2 -> distribution.setStar2(distribution.getStar2() + 1);
          case 3 -> distribution.setStar3(distribution.getStar3() + 1);
          case 4 -> distribution.setStar4(distribution.getStar4() + 1);
          case 5 -> distribution.setStar5(distribution.getStar5() + 1);
          default -> { }
        }
      }

      if ("highest".equalsIgnoreCase(sort)) {
        allRatings.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));
      } else if ("lowest".equalsIgnoreCase(sort)) {
        allRatings.sort((a, b) -> Integer.compare(a.getScore(), b.getScore()));
      } else {
        allRatings.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
      }

      double avg = allRatings.isEmpty() ? 0.0 :
          allRatings.stream().mapToInt(RatingResponse::getScore).average().orElse(0.0);
      avg = Math.round(avg * 10.0) / 10.0;

      int fromIndex = Math.min(page * size, allRatings.size());
      int toIndex = Math.min(fromIndex + size, allRatings.size());
      List<RatingResponse> paged = allRatings.subList(fromIndex, toIndex);

      return PagedRatingResponse.builder()
          .averageRating(avg)
          .ratingCount(allRatings.size())
          .distribution(distribution)
          .ratings(paged)
          .hasMore(toIndex < allRatings.size())
          .build();
    } catch (InterruptedException | ExecutionException e) {
      log.error("Error fetching ratings for recipe {}", recipeId, e);
      throw new RuntimeException("Failed to fetch ratings", e);
    }
  }

  /**
   * Delete rating.
   */
  public void deleteRating(String recipeId, String userId) {
    if (firestore == null) {
      mockRatings.remove(recipeId + "_" + userId);
      return;
    }

    try {
      String ratingDocId = recipeId + "_" + userId;
      DocumentReference docRef = firestore.collection(ratingsCollection).document(ratingDocId);
      docRef.delete().get();
      recalculateRecipeRating(recipeId);
    } catch (InterruptedException | ExecutionException e) {
      log.error("Error deleting rating for recipe {}", recipeId, e);
      throw new RuntimeException("Failed to delete rating", e);
    }
  }

  private void recalculateRecipeRating(String recipeId)
      throws ExecutionException, InterruptedException {
    Query query = firestore.collection(ratingsCollection).whereEqualTo("recipeId", recipeId);
    QuerySnapshot snapshot = query.get().get();
    int count = snapshot.size();
    double avg = 0.0;
    if (count > 0) {
      avg = snapshot.getDocuments().stream()
          .mapToDouble(d -> d.getDouble("score") != null ? d.getDouble("score") : 0.0)
          .average().orElse(0.0);
      avg = Math.round(avg * 10.0) / 10.0;
    }

    Map<String, Object> update = new HashMap<>();
    update.put("averageRating", avg);
    update.put("ratingCount", count);
    firestore.collection(recipesCollection).document(recipeId).update(update).get();
  }

  private RatingResponse mapToResponse(Map<String, Object> data) {
    Object scoreObj = data.get("score");
    int score = scoreObj instanceof Long ? ((Long) scoreObj).intValue() : (Integer) scoreObj;

    return RatingResponse.builder()
        .id((String) data.get("id"))
        .recipeId((String) data.get("recipeId"))
        .userId((String) data.get("userId"))
        .authorName((String) data.get("authorName"))
        .authorAvatarUrl((String) data.get("authorAvatarUrl"))
        .score(score)
        .reviewText((String) data.get("reviewText"))
        .createdAt((String) data.get("createdAt"))
        .updatedAt((String) data.get("updatedAt"))
        .build();
  }

  private RatingResponse saveToMockRatings(String recipeId, CreateRatingRequest request,
      String userId) {
    String docId = recipeId + "_" + userId;
    Map<String, Object> data = new HashMap<>();
    data.put("id", docId);
    data.put("recipeId", recipeId);
    data.put("userId", userId);
    data.put("authorName", "Chef User");
    data.put("score", request.getScore());
    data.put("reviewText", request.getReviewText());
    data.put("createdAt", Instant.now().toString());
    data.put("updatedAt", Instant.now().toString());

    mockRatings.put(docId, data);
    return mapToResponse(data);
  }

  private PagedRatingResponse getFromMockRatings(String recipeId) {
    List<RatingResponse> list = new ArrayList<>();
    RatingDistribution distribution = new RatingDistribution();
    mockRatings.values().forEach(data -> {
      if (recipeId.equals(data.get("recipeId"))) {
        list.add(mapToResponse(data));
      }
    });
    double avg = list.isEmpty() ? 0.0 :
        list.stream().mapToInt(RatingResponse::getScore).average().orElse(0.0);
    return PagedRatingResponse.builder()
        .averageRating(avg)
        .ratingCount(list.size())
        .distribution(distribution)
        .ratings(list)
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
      } catch (FirebaseAuthException e) {
        // Ignore
      }
    }
    return "Chef " + uid.substring(0, Math.min(5, uid.length()));
  }
}
