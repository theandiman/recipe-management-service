package com.recipe.storage.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.AggregateQuerySnapshot;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.recipe.shared.model.NutritionalInfo;
import com.recipe.shared.model.Recipe;
import com.recipe.storage.dto.CreateRecipeRequest;
import com.recipe.storage.dto.PagedRecipeResponse;
import com.recipe.storage.dto.RecipeResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
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
 * Service for managing recipes in Firestore.
 */
@Slf4j
@Service
public class RecipeService {

  @Autowired(required = false)
  private Firestore firestore;

  @Autowired(required = false)
  private FirebaseAuth firebaseAuth;

  @Autowired(required = false)
  private FollowService followService;

  @Value("${firestore.collection.recipes}")
  private String recipesCollection;

  @Value("${firestore.collection.saved-recipes:savedRecipes}")
  private String savedRecipesCollection;

  @Value("${firestore.collection.likes:likes}")
  private String likesCollection;

  // In-memory store for testing when Firestore is not available
  private final ConcurrentHashMap<String, Recipe> mockStore = new ConcurrentHashMap<>();

  /**
   * Save a new recipe to Firestore.
   *
   * @param request The recipe creation request
   * @param userId  The Firebase user ID of the recipe creator
   * @return The saved recipe with generated ID
   */
  public RecipeResponse saveRecipe(CreateRecipeRequest request, String userId) {
    if (firestore == null) {
      log.warn("Firestore not configured - saving to in-memory store");
      return saveToMockStore(request, userId);
    }

    try {
      String recipeId = UUID.randomUUID().toString();
      Instant now = Instant.now();

      NutritionalInfo nutritionalInfo = mapToNutritionalInfo(request.getNutrition());
      com.recipe.shared.model.RecipeTips recipeTips = mapToRecipeTips(request.getTips());

      Recipe recipe = Recipe.builder()
          .id(recipeId)
          .userId(userId)
          .recipeName(request.getTitle())
          .description(request.getDescription())
          .ingredients(request.getIngredients())
          .instructions(request.getInstructions())
          .prepTimeMinutes(request.getPrepTime())
          .cookTimeMinutes(request.getCookTime())
          .totalTimeMinutes(request.getTotalTime())
          .servings(request.getServings())
          .nutritionalInfo(nutritionalInfo)
          .tips(recipeTips)
          .imageUrl(request.getImageUrl())
          .source(request.getSource())
          .tags(request.getTags())
          .dietaryRestrictions(request.getDietaryRestrictions())
          .publicRecipe(request.isPublic())
          .createdAt(now)
          .updatedAt(now)
          .build();

      DocumentReference docRef = firestore.collection(recipesCollection).document(recipeId);
      ApiFuture<WriteResult> future = docRef.set(recipe);

      // Wait for the write to complete
      WriteResult result = future.get();
      log.info("Recipe saved with ID: {} at {}", recipeId, result.getUpdateTime());

      return mapToResponse(recipe);
    } catch (InterruptedException | ExecutionException e) {
      log.error("Error saving recipe to Firestore", e);
      throw new RuntimeException("Failed to save recipe", e);
    }
  }

  /**
   * Update an existing recipe.
   *
   * @param recipeId The ID of the recipe to update
   * @param request  The update request
   * @param userId   The ID of the user attempting the update
   * @return The updated recipe response
   */
  public RecipeResponse updateRecipe(String recipeId, CreateRecipeRequest request, String userId) {
    if (firestore == null) {
      return updateInMockStore(recipeId, request, userId);
    }

    try {
      DocumentReference docRef = firestore.collection(recipesCollection).document(recipeId);
      ApiFuture<DocumentSnapshot> future = docRef.get();
      DocumentSnapshot document = future.get();

      if (!document.exists()) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found");
      }

      Recipe existingRecipe = document.toObject(Recipe.class);
      if (existingRecipe == null) {
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
            "Failed to load existing recipe");
      }

      // Verify ownership
      if (!userId.equals(existingRecipe.getUserId())) {
        log.warn("User {} attempted to update recipe {} owned by {}",
            userId, recipeId, existingRecipe.getUserId());
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
      }

      Instant now = Instant.now();

      NutritionalInfo nutritionalInfo = mapToNutritionalInfo(request.getNutrition());
      com.recipe.shared.model.RecipeTips recipeTips = mapToRecipeTips(request.getTips());

      // Update fields
      Recipe updatedRecipe = existingRecipe.toBuilder()
          .recipeName(request.getTitle())
          .description(request.getDescription())
          .ingredients(request.getIngredients())
          .instructions(request.getInstructions())
          .prepTimeMinutes(request.getPrepTime())
          .cookTimeMinutes(request.getCookTime())
          .totalTimeMinutes(request.getTotalTime())
          .servings(request.getServings())
          .nutritionalInfo(nutritionalInfo)
          .tips(recipeTips)
          .imageUrl(request.getImageUrl())
          .source(request.getSource())
          .tags(request.getTags())
          .dietaryRestrictions(request.getDietaryRestrictions())
          .publicRecipe(request.isPublic())
          .updatedAt(now)
          .build();

      ApiFuture<WriteResult> writeFuture = docRef.set(updatedRecipe);
      writeFuture.get();

      log.info("Recipe updated with ID: {} by user {}", recipeId, userId);
      return mapToResponse(updatedRecipe);
    } catch (InterruptedException | ExecutionException e) {
      log.error("Error updating recipe in Firestore", e);
      throw new RuntimeException("Failed to update recipe", e);
    }
  }

  /**
   * Save a recipe to the in-memory mock store.
   */
  private RecipeResponse saveToMockStore(CreateRecipeRequest request, String userId) {
    String recipeId = UUID.randomUUID().toString();
    Instant now = Instant.now();

    NutritionalInfo nutritionalInfo = mapToNutritionalInfo(request.getNutrition());
    com.recipe.shared.model.RecipeTips recipeTips = mapToRecipeTips(request.getTips());

    Recipe recipe = Recipe.builder()
        .id(recipeId)
        .userId(userId)
        .recipeName(request.getTitle())
        .description(request.getDescription())
        .ingredients(request.getIngredients())
        .instructions(request.getInstructions())
        .prepTimeMinutes(request.getPrepTime())
        .cookTimeMinutes(request.getCookTime())
        .totalTimeMinutes(request.getTotalTime())
        .servings(request.getServings())
        .nutritionalInfo(nutritionalInfo)
        .tips(recipeTips)
        .imageUrl(request.getImageUrl())
        .source(request.getSource())
        .tags(request.getTags())
        .dietaryRestrictions(request.getDietaryRestrictions())
        .publicRecipe(request.isPublic())
        .createdAt(now)
        .updatedAt(now)
        .build();

    mockStore.put(recipeId, recipe);
    log.info("Mock recipe saved with ID: {}", recipeId);
    return mapToResponse(recipe);
  }

  /**
   * Update a recipe in the in-memory mock store.
   */
  private RecipeResponse updateInMockStore(String recipeId, CreateRecipeRequest request,
      String userId) {
    Recipe existingRecipe = mockStore.get(recipeId);
    if (existingRecipe == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found");
    }

    if (!userId.equals(existingRecipe.getUserId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
    }

    Instant now = Instant.now();
    NutritionalInfo nutritionalInfo = mapToNutritionalInfo(request.getNutrition());
    com.recipe.shared.model.RecipeTips recipeTips = mapToRecipeTips(request.getTips());

    Recipe updatedRecipe = existingRecipe.toBuilder()
        .recipeName(request.getTitle())
        .description(request.getDescription())
        .ingredients(request.getIngredients())
        .instructions(request.getInstructions())
        .prepTimeMinutes(request.getPrepTime())
        .cookTimeMinutes(request.getCookTime())
        .totalTimeMinutes(request.getTotalTime())
        .servings(request.getServings())
        .nutritionalInfo(nutritionalInfo)
        .tips(recipeTips)
        .imageUrl(request.getImageUrl())
        .source(request.getSource())
        .tags(request.getTags())
        .dietaryRestrictions(request.getDietaryRestrictions())
        .publicRecipe(request.isPublic())
        .updatedAt(now)
        .build();

    mockStore.put(recipeId, updatedRecipe);
    log.info("Mock recipe updated with ID: {}", recipeId);
    return mapToResponse(updatedRecipe);
  }

  /**
   * Get a recipe from the in-memory mock store.
   */
  private RecipeResponse getFromMockStore(String recipeId, String userId) {
    Recipe recipe = mockStore.get(recipeId);
    if (recipe == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found");
    }

    if (!userId.equals(recipe.getUserId()) && !recipe.isPublicRecipe()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
    }

    return mapToResponse(recipe);
  }

  /**
   * Delete a recipe from the in-memory mock store.
   */
  private void deleteFromMockStore(String recipeId, String userId) {
    Recipe recipe = mockStore.get(recipeId);
    if (recipe == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found");
    }

    if (!userId.equals(recipe.getUserId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
    }

    mockStore.remove(recipeId);
    log.info("Mock recipe deleted with ID: {}", recipeId);
  }

  /**
   * Update recipe sharing in the in-memory mock store.
   */
  private RecipeResponse updateSharingInMockStore(String recipeId, boolean isPublic,
      String userId) {
    Recipe existingRecipe = mockStore.get(recipeId);
    if (existingRecipe == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found");
    }

    if (!userId.equals(existingRecipe.getUserId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
    }

    Recipe updatedRecipe = existingRecipe.toBuilder()
        .publicRecipe(isPublic)
        .updatedAt(Instant.now())
        .build();

    mockStore.put(recipeId, updatedRecipe);
    return mapToResponse(updatedRecipe);
  }

  /**
   * Get user recipes from mock store.
   */
  private List<RecipeResponse> getMockUserRecipes(String userId) {
    List<RecipeResponse> recipes = new ArrayList<>();
    mockStore.values().forEach(recipe -> {
      if (userId.equals(recipe.getUserId())) {
        recipes.add(mapToResponse(recipe));
      }
    });
    // Sort recently created first
    recipes.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
    return recipes;
  }

  /**
   * Get public recipes from mock store.
   */
  private List<RecipeResponse> getMockPublicRecipes() {
    List<RecipeResponse> recipes = new ArrayList<>();
    mockStore.values().forEach(recipe -> {
      if (recipe.isPublicRecipe()) {
        recipes.add(mapToResponse(recipe));
      }
    });
    // Sort recently created first
    recipes.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
    return recipes;
  }

  /**
   * Get all recipes for a specific user.
   *
   * @param userId The Firebase user ID
   * @return List of recipes belonging to the user
   */
  public List<RecipeResponse> getUserRecipes(String userId) {
    if (firestore == null) {
      log.warn("Firestore not configured - returning from in-memory store");
      return getMockUserRecipes(userId);
    }

    try {
      // Note: Removed orderBy to avoid needing composite index
      // Recipes are returned in document creation order
      // TODO: Add composite index and re-enable orderBy for better UX
      Query query = firestore.collection(recipesCollection)
          .whereEqualTo("userId", userId);

      ApiFuture<QuerySnapshot> future = query.get();
      QuerySnapshot querySnapshot = future.get();

      List<RecipeResponse> recipes = new ArrayList<>();
      querySnapshot.getDocuments().forEach(doc -> {
        Recipe recipe = doc.toObject(Recipe.class);
        RecipeResponse response = mapToResponse(recipe);
        response.setLikeCount(extractLikeCount(doc));
        recipes.add(response);
      });

      // Sort in-memory by createdAt (newest first)
      recipes.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));

      // Batch-check saved status only for the recipes being returned (avoids full collection read)
      if (!recipes.isEmpty()) {
        List<DocumentReference> bookmarkRefs = new ArrayList<>();
        for (RecipeResponse r : recipes) {
          bookmarkRefs.add(firestore
              .collection(savedRecipesCollection)
              .document(userId)
              .collection("recipes")
              .document(r.getId()));
        }
        List<DocumentSnapshot> bookmarkDocs = firestore
            .getAll(bookmarkRefs.toArray(new DocumentReference[0]))
            .get();
        for (int i = 0; i < bookmarkDocs.size(); i++) {
          recipes.get(i).setSavedByCurrentUser(bookmarkDocs.get(i).exists());
        }

        // Batch-check like status
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

      log.info("Found {} recipes for user {}", recipes.size(), userId);
      return recipes;
    } catch (InterruptedException | ExecutionException e) {
      log.error("Error fetching recipes from Firestore", e);
      throw new RuntimeException("Failed to fetch recipes", e);
    }
  }

  /**
   * Get public recipes with cursor-based pagination.
   *
   * @param pageToken Opaque cursor token from a previous response (null for first page)
   * @param size Number of recipes per page (min 1, max 100)
   * @return Paginated list of public recipes
   */
  public PagedRecipeResponse getPublicRecipes(String pageToken, int size) {
    if (size < 1) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page size must be at least 1");
    }
    if (size > 100) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page size must not exceed 100");
    }

    // Decode and validate the cursor token early to fail fast on bad input
    Timestamp cursor = null;
    if (pageToken != null && !pageToken.isEmpty()) {
      cursor = decodePageToken(pageToken);
    }

    if (firestore == null) {
      log.warn("Firestore not configured - returning from in-memory store");
      List<RecipeResponse> mockRecipes = getMockPublicRecipes();
      return PagedRecipeResponse.builder()
          .recipes(mockRecipes)
          .size(size)
          .totalCount(mockRecipes.size())
          .nextPageToken(null)
          .build();
    }

    try {
      Query baseQuery = firestore.collection(recipesCollection)
          .whereEqualTo("isPublic", true);

      ApiFuture<AggregateQuerySnapshot> countFuture = baseQuery.count().get();
      AggregateQuerySnapshot countSnapshot = countFuture.get();
      final long totalCount = countSnapshot.getCount();

      Query pagedQuery = baseQuery.orderBy("createdAt", Query.Direction.DESCENDING);
      if (cursor != null) {
        pagedQuery = pagedQuery.startAfter(cursor);
      }
      pagedQuery = pagedQuery.limit(size);

      ApiFuture<QuerySnapshot> future = pagedQuery.get();
      QuerySnapshot querySnapshot = future.get();

      List<RecipeResponse> recipes = new ArrayList<>();
      Map<String, String> displayNameCache = new HashMap<>();
      querySnapshot.getDocuments().forEach(doc -> {
        Recipe recipe = doc.toObject(Recipe.class);
        RecipeResponse response = mapToResponse(recipe);
        response.setLikeCount(extractLikeCount(doc));
        String uid = recipe.getUserId();
        if (uid != null) {
          if (!displayNameCache.containsKey(uid)) {
            displayNameCache.put(uid, resolveDisplayName(uid));
          }
          response.setAuthorDisplayName(displayNameCache.get(uid));
        }
        recipes.add(response);
      });

      String nextPageToken = encodeNextPageToken(querySnapshot);
      log.info("Found {} public recipes (size={}, total={})",
          recipes.size(), size, totalCount);
      return PagedRecipeResponse.builder()
          .recipes(recipes)
          .size(size)
          .totalCount(totalCount)
          .nextPageToken(nextPageToken)
          .build();
    } catch (InterruptedException | ExecutionException e) {
      log.error("Error fetching public recipes from Firestore", e);
      throw new RuntimeException("Failed to fetch public recipes", e);
    }
  }

  /**
   * Get the personalised feed for a user: public recipes from users they follow,
   * ordered by creation date descending with cursor-based pagination.
   *
   * @param userId    the Firebase UID of the requesting user
   * @param pageToken opaque cursor token from a previous response (null for first page)
   * @param size      number of recipes per page (min 1, max 100)
   * @return paginated feed of public recipes from followed users
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
      cursor = decodePageToken(pageToken);
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

    // The same createdAt timestamp cursor is applied to each batch independently.
    // This is correct because createdAt is a global ordering field shared by all recipes;
    // every batch skips the same point in time, so merging and re-sorting across batches
    // produces a consistent total ordering.
    List<List<String>> batches = partitionList(followingIds, 30);
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
        // Fetch size+1 items per batch so we can detect whether a next page exists after merging.
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

      // allRecipes.size() > size means there is at least one more result beyond this page.
      boolean hasNextPage = allRecipes.size() > size;
      List<Recipe> page = allRecipes.subList(0, Math.min(size, allRecipes.size()));

      List<RecipeResponse> recipes = new ArrayList<>();
      Map<String, String> displayNameCache = new HashMap<>();
      for (Recipe recipe : page) {
        RecipeResponse response = mapToResponse(recipe);
        response.setLikeCount(
            likeCountByRecipeId.getOrDefault(recipe.getId(), 0L).intValue());
        String uid = recipe.getUserId();
        if (uid != null) {
          if (!displayNameCache.containsKey(uid)) {
            displayNameCache.put(uid, resolveDisplayName(uid));
          }
          response.setAuthorDisplayName(displayNameCache.get(uid));
        }
        recipes.add(response);
      }

      // Batch-check like status for all feed recipes
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

  private PagedRecipeResponse emptyFeedResponse(int size) {
    return PagedRecipeResponse.builder()
        .recipes(new ArrayList<>())
        .size(size)
        .totalCount(0)
        .nextPageToken(null)
        .build();
  }

  private static <T> List<List<T>> partitionList(List<T> list, int batchSize) {
    List<List<T>> partitions = new ArrayList<>();
    for (int i = 0; i < list.size(); i += batchSize) {
      partitions.add(list.subList(i, Math.min(i + batchSize, list.size())));
    }
    return partitions;
  }

  /**
   * Decodes an opaque page token into a Firestore {@link Timestamp} cursor.
   *
   * <p>The token is a URL-safe base64 string encoding {@code "<seconds>,<nanos>"}.
   * Throws {@code 400 Bad Request} if the token is malformed or cannot be decoded.
   *
   * @param pageToken the opaque cursor token from a previous paged response
   * @return the decoded Firestore Timestamp to pass to {@code startAfter()}
   */
  private Timestamp decodePageToken(String pageToken) {
    try {
      byte[] decoded = Base64.getUrlDecoder().decode(pageToken);
      String cursor = new String(decoded, StandardCharsets.UTF_8);
      String[] parts = cursor.split(",", 2);
      if (parts.length != 2) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page token");
      }
      return Timestamp.ofTimeSecondsAndNanos(
          Long.parseLong(parts[0]), Integer.parseInt(parts[1]));
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page token");
    }
  }

  /**
   * Encodes the {@code createdAt} timestamp of the last document in a query snapshot
   * into an opaque page token for cursor-based pagination.
   *
   * <p>Returns {@code null} when the snapshot is empty or the last document has no
   * {@code createdAt} timestamp, indicating there is no next page.
   *
   * @param querySnapshot the Firestore query result snapshot
   * @return a URL-safe base64 cursor token, or {@code null} if no next page exists
   */
  private String encodeNextPageToken(QuerySnapshot querySnapshot) {
    return encodeNextPageTokenFromField(querySnapshot, "createdAt");
  }

  /**
   * Encodes the given timestamp field of the last document in a query snapshot
   * into an opaque page token for cursor-based pagination.
   *
   * @param querySnapshot the Firestore query result snapshot
   * @param fieldName     the name of the timestamp field to use as cursor
   * @return a URL-safe base64 cursor token, or {@code null} if no next page exists
   */
  private String encodeNextPageTokenFromField(QuerySnapshot querySnapshot, String fieldName) {
    if (querySnapshot.isEmpty()) {
      return null;
    }
    List<? extends DocumentSnapshot> docs = querySnapshot.getDocuments();
    DocumentSnapshot lastDoc = docs.get(docs.size() - 1);
    Timestamp lastTimestamp = lastDoc.getTimestamp(fieldName);
    if (lastTimestamp == null) {
      return null;
    }
    String cursor = lastTimestamp.getSeconds() + "," + lastTimestamp.getNanos();
    return Base64.getUrlEncoder().withoutPadding()
        .encodeToString(cursor.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Get a public recipe by ID without authentication.
   * Returns 404 if the recipe does not exist or is not public.
   *
   * @param recipeId The recipe ID
   * @return The recipe if it exists and is public
   * @throws ResponseStatusException 404 if not found or not public
   */
  public RecipeResponse getPublicRecipe(String recipeId) {
    if (firestore == null) {
      log.warn("Firestore not configured - cannot fetch recipe");
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Recipe service unavailable");
    }

    try {
      DocumentReference docRef = firestore.collection(recipesCollection).document(recipeId);
      ApiFuture<DocumentSnapshot> future = docRef.get();
      DocumentSnapshot document = future.get();

      if (document == null || !document.exists()) {
        log.warn("Public recipe not found: {}", recipeId);
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found");
      }

      Recipe recipe = document.toObject(Recipe.class);
      if (recipe == null) {
        log.error("Failed to deserialize recipe: {}", recipeId);
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found");
      }

      // Return 404 for private recipes to avoid leaking existence
      if (!recipe.isPublicRecipe()) {
        log.warn("Unauthenticated access attempt to private recipe: {}", recipeId);
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found");
      }

      log.info("Retrieved public recipe {}", recipeId);
      RecipeResponse response = mapToResponse(recipe);
      response.setAuthorDisplayName(resolveDisplayName(recipe.getUserId()));
      response.setLikeCount(extractLikeCount(document));
      return response;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Interrupted while fetching public recipe from Firestore", e);
      throw new RuntimeException("Failed to fetch recipe", e);
    } catch (ExecutionException e) {
      log.error("Error fetching public recipe from Firestore", e);
      throw new RuntimeException("Failed to fetch recipe", e);
    }
  }

  /**
   * Get a specific recipe by ID.
   *
   * @param recipeId The recipe ID
   * @param userId   The Firebase user ID (for authorization)
   * @return The recipe if found and user has access
   * @throws ResponseStatusException if recipe not found or user doesn't have
   *                                 access
   */
  public RecipeResponse getRecipe(String recipeId, String userId) {
    if (firestore == null) {
      log.warn("Firestore not configured - fetching from in-memory store");
      return getFromMockStore(recipeId, userId);
    }

    try {
      DocumentReference docRef = firestore.collection(recipesCollection).document(recipeId);
      ApiFuture<DocumentSnapshot> future = docRef.get();
      DocumentSnapshot document = future.get();

      if (!document.exists()) {
        log.warn("Recipe not found: {}", recipeId);
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found");
      }

      Recipe recipe = document.toObject(Recipe.class);
      if (recipe == null) {
        log.error("Failed to deserialize recipe: {}", recipeId);
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
            "Failed to load recipe");
      }

      // Verify user has access (owner or public)
      if (!userId.equals(recipe.getUserId()) && !recipe.isPublicRecipe()) {
        log.warn("User {} attempted to access private recipe {} owned by {}",
            userId, recipeId, recipe.getUserId());
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
      }

      log.info("Retrieved recipe {} for user {}", recipeId, userId);
      RecipeResponse response = mapToResponse(recipe);
      response.setSavedByCurrentUser(isRecipeSavedByUser(recipeId, userId));
      response.setLikeCount(extractLikeCount(document));
      response.setLikedByCurrentUser(isRecipeLikedByUser(recipeId, userId));
      return response;
    } catch (InterruptedException | ExecutionException e) {
      log.error("Error fetching recipe from Firestore", e);
      throw new RuntimeException("Failed to fetch recipe", e);
    }
  }

  /**
   * Delete a recipe by ID.
   *
   * @param recipeId The recipe ID
   * @param userId   The Firebase user ID (for authorization)
   * @throws ResponseStatusException if recipe not found or user doesn't have
   *                                 access
   */
  public void deleteRecipe(String recipeId, String userId) {
    if (firestore == null) {
      log.warn("Firestore not configured - deleting from in-memory store");
      deleteFromMockStore(recipeId, userId);
      return;
    }

    try {
      DocumentReference docRef = firestore.collection(recipesCollection).document(recipeId);
      ApiFuture<DocumentSnapshot> future = docRef.get();
      DocumentSnapshot document = future.get();

      if (!document.exists()) {
        log.warn("Recipe not found for deletion: {}", recipeId);
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found");
      }

      Recipe recipe = document.toObject(Recipe.class);
      if (recipe == null) {
        log.error("Failed to deserialize recipe for deletion: {}", recipeId);
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
            "Failed to load recipe");
      }

      // Verify user owns this recipe
      if (!userId.equals(recipe.getUserId())) {
        log.warn("User {} attempted to delete recipe {} owned by {}",
            userId, recipeId, recipe.getUserId());
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
      }

      // Delete the document
      ApiFuture<WriteResult> deleteFuture = docRef.delete();
      WriteResult result = deleteFuture.get();

      log.info("Deleted recipe {} for user {} at {}", recipeId, userId, result.getUpdateTime());
    } catch (InterruptedException | ExecutionException e) {
      log.error("Error deleting recipe from Firestore", e);
      throw new RuntimeException("Failed to delete recipe", e);
    }
  }

  /**
   * Update the sharing status (isPublic) of a recipe.
   *
   * @param recipeId The recipe ID
   * @param isPublic The new sharing status
   * @param userId   The Firebase user ID (for authorization)
   * @return The updated recipe
   * @throws ResponseStatusException if recipe not found or user doesn't own it
   */
  public RecipeResponse updateRecipeSharing(String recipeId, boolean isPublic, String userId) {
    if (firestore == null) {
      log.warn("Firestore not configured - updating sharing entirely in memory");
      return updateSharingInMockStore(recipeId, isPublic, userId);
    }

    try {
      DocumentReference docRef = firestore.collection(recipesCollection).document(recipeId);
      ApiFuture<DocumentSnapshot> future = docRef.get();
      DocumentSnapshot document = future.get();

      if (!document.exists()) {
        log.warn("Recipe not found: {}", recipeId);
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found");
      }

      Recipe existingRecipe = document.toObject(Recipe.class);
      if (existingRecipe == null) {
        log.error("Failed to deserialize recipe: {}", recipeId);
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
            "Failed to load recipe");
      }

      // Verify ownership
      if (!userId.equals(existingRecipe.getUserId())) {
        log.warn("User {} attempted to update sharing for recipe {} owned by {}",
            userId, recipeId, existingRecipe.getUserId());
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
      }

      // Update only the isPublic field and updatedAt timestamp
      Recipe updatedRecipe = existingRecipe.toBuilder()
          .publicRecipe(isPublic)
          .updatedAt(Instant.now())
          .build();

      log.info("Updating recipe {} - current: {}, new: {}, actual: {}",
          recipeId, existingRecipe.isPublicRecipe(), isPublic, updatedRecipe.isPublicRecipe());

      ApiFuture<WriteResult> writeFuture = docRef.update("isPublic",
          updatedRecipe.isPublicRecipe(), "updatedAt", updatedRecipe.getUpdatedAt());
      writeFuture.get();

      log.info("Updated sharing status for recipe {} to {} by user {}",
          recipeId, isPublic, userId);
      return mapToResponse(updatedRecipe);
    } catch (InterruptedException | ExecutionException e) {
      log.error("Error updating recipe sharing in Firestore", e);
      throw new RuntimeException("Failed to update recipe sharing", e);
    }
  }

  /**
   * Save (bookmark) a recipe for a user. Idempotent – calling this multiple times has
   * no additional effect.
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
      // Verify the recipe exists before bookmarking it
      DocumentReference recipeDocRef = firestore.collection(recipesCollection).document(recipeId);
      DocumentSnapshot recipeDoc = recipeDocRef.get().get();

      if (!recipeDoc.exists()) {
        log.warn("Attempt to save non-existent recipe {}", recipeId);
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found");
      }

      Recipe recipe = recipeDoc.toObject(Recipe.class);
      // Enforce access: treat private recipes not owned by this user as if they don't exist
      // (to avoid leaking existence of private content)
      if (recipe != null && !recipe.isPublicRecipe() && !userId.equals(recipe.getUserId())) {
        log.warn("User {} attempted to save private recipe {} owned by {}",
            userId, recipeId, recipe.getUserId());
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found");
      }

      // Only create the bookmark if it does not already exist so savedAt reflects first save time
      DocumentReference savedDocRef = firestore
          .collection(savedRecipesCollection)
          .document(userId)
          .collection("recipes")
          .document(recipeId);

      firestore.runTransaction(transaction -> {
        DocumentSnapshot existingSavedDoc = transaction.get(savedDocRef).get();
        if (!existingSavedDoc.exists()) {
          Map<String, Object> data = new HashMap<>();
          data.put("savedAt", com.google.cloud.Timestamp.now());
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
   * Unsave (remove bookmark) a recipe for a user. Idempotent – calling this when the recipe is
   * not saved is a no-op.
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
      // Idempotent delete: no-op if the document does not exist
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
   * Like a recipe for a user. Idempotent – calling this multiple times has no additional effect.
   * Atomically creates a like document and increments the {@code likeCount} on the recipe.
   *
   * @param recipeId The recipe ID to like
   * @param userId   The Firebase user ID
   * @throws ResponseStatusException 404 if the recipe does not exist,
   *                                 503 if Firestore is unavailable
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

      firestore.runTransaction(transaction -> {
        DocumentSnapshot existingLike = transaction.get(likeDocRef).get();
        if (!existingLike.exists()) {
          transaction.set(likeDocRef,
              java.util.Map.of("likedAt", FieldValue.serverTimestamp()));
          transaction.update(recipeDocRef, "likeCount", FieldValue.increment(1));
        }
        return null;
      }).get();

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
   * Unlike a recipe for a user. Idempotent – calling this when the recipe is not liked is a no-op.
   * Atomically removes the like document and decrements the {@code likeCount} on the recipe.
   *
   * @param recipeId The recipe ID to unlike
   * @param userId   The Firebase user ID
   * @throws ResponseStatusException 503 if Firestore is unavailable
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
   * Get the paginated list of recipes saved (bookmarked) by a user,
   * ordered by save date (newest first).
   *
   * @param userId    The Firebase user ID
   * @param pageToken Opaque cursor token from a previous response (null for first page)
   * @param size      Number of recipes per page (min 1, max 100)
   * @return Paginated list of saved recipes
   */
  public PagedRecipeResponse getSavedRecipes(String userId, String pageToken, int size) {
    if (size < 1) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page size must be at least 1");
    }
    if (size > 100) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page size must not exceed 100");
    }

    // Validate cursor early to fail fast on bad input
    com.google.cloud.Timestamp cursor = null;
    if (pageToken != null && !pageToken.isEmpty()) {
      cursor = decodePageToken(pageToken);
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
      com.google.cloud.firestore.CollectionReference savedRef = firestore
          .collection(savedRecipesCollection)
          .document(userId)
          .collection("recipes");

      // Total count of saved recipes
      final long totalCount = savedRef.count().get().get().getCount();

      // Build cursor-paginated query ordered by savedAt descending
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
            // Only include recipes accessible to the user (public or owned by them)
            if (recipe != null
                && (recipe.isPublicRecipe() || userId.equals(recipe.getUserId()))) {
              RecipeResponse response = mapToResponse(recipe);
              response.setSavedByCurrentUser(true);
              response.setLikeCount(extractLikeCount(recipeDoc));
              recipes.add(response);
            }
          }
        }

        // Batch-check like status for all saved recipes
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

      String nextToken = encodeNextPageTokenFromField(querySnapshot, "savedAt");
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
   * Check whether a specific recipe is liked by a user.
   *
   * @param recipeId The recipe ID
   * @param userId   The Firebase user ID
   * @return {@code true} if the recipe is liked, {@code false} otherwise
   *         (including when Firestore is unavailable)
   */
  private boolean isRecipeLikedByUser(String recipeId, String userId) {
    if (firestore == null) {
      return false;
    }
    try {
      DocumentSnapshot doc = firestore
          .collection(likesCollection)
          .document(recipeId)
          .collection("users")
          .document(userId)
          .get().get();
      return doc.exists();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Interrupted while checking like status for recipe {} user {}: {}",
          recipeId, userId, e.getMessage());
      return false;
    } catch (ExecutionException e) {
      log.warn("Failed to check like status for recipe {} user {}: {}",
          recipeId, userId, e.getMessage());
      return false;
    }
  }

  /**
   * Extract the like count from a Firestore document snapshot.
   *
   * @param doc The Firestore document snapshot
   * @return The like count, or 0 if not present
   */
  private int extractLikeCount(DocumentSnapshot doc) {
    Long count = doc.getLong("likeCount");
    return count != null ? count.intValue() : 0;
  }

  /**
   * Check whether a specific recipe is saved (bookmarked) by a user.
   *
   * @param recipeId The recipe ID
   * @param userId   The Firebase user ID
   * @return {@code true} if the recipe is saved, {@code false} otherwise
   *         (including when Firestore is unavailable)
   */
  private boolean isRecipeSavedByUser(String recipeId, String userId) {
    if (firestore == null) {
      return false;
    }
    try {
      DocumentSnapshot doc = firestore
          .collection(savedRecipesCollection)
          .document(userId)
          .collection("recipes")
          .document(recipeId)
          .get().get();
      return doc.exists();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Interrupted while checking saved status for recipe {} user {}: {}",
          recipeId, userId, e.getMessage());
      return false;
    } catch (ExecutionException e) {
      log.warn("Failed to check saved status for recipe {} user {}: {}",
          recipeId, userId, e.getMessage());
      return false;
    }
  }

  /**
   * Map Recipe entity to RecipeResponse DTO.
   */
  RecipeResponse mapToResponse(Recipe recipe) {
    Map<String, Object> nutritionMap = null;
    if (recipe.getNutritionalInfo() != null
        && recipe.getNutritionalInfo().getPerServing() != null) {
      // Simplified mapping: we return only perServing values as flat map to match API
      // contract
      nutritionMap = recipe.getNutritionalInfo().getPerServing().toMap();
    }

    Map<String, List<String>> tipsMap = null;
    if (recipe.getTips() != null) {
      // Simplified mapping for now, based on RecipeTips having simple conversion
      // available
      // Note: RecipeTips.toMap() currently returns null or specific map, need to
      // check implementation
      // The shared model toMap returns "substitutions" and "variations" only.
      try {
        tipsMap = recipe.getTips().toMap();
      } catch (Exception e) {
        log.warn("Failed to map tips", e);
        tipsMap = null;
      }
    }

    return RecipeResponse.builder()
        .id(recipe.getId())
        .userId(recipe.getUserId())
        .title(recipe.getRecipeName())
        .description(recipe.getDescription())
        .ingredients(recipe.getIngredients())
        .instructions(recipe.getInstructions())
        .prepTime(recipe.getPrepTimeMinutes())
        .cookTime(recipe.getCookTimeMinutes())
        .totalTime(recipe.getCalculatedTotalTimeMinutes())
        .servings(recipe.getServings())
        .nutrition(nutritionMap)
        .tips(tipsMap)
        .imageUrl(recipe.getImageUrl())
        .source(recipe.getSource())
        .createdAt(recipe.getCreatedAt())
        .updatedAt(recipe.getUpdatedAt())
        .tags(recipe.getTags())
        .dietaryRestrictions(recipe.getDietaryRestrictions())
        .isPublic(recipe.isPublicRecipe())
        .build();
  }

  /**
   * Resolve a Firebase user's display name, returning null on failure.
   *
   * @param userId The Firebase user ID
   * @return The display name, or null if lookup fails or auth is unavailable
   */
  private String resolveDisplayName(String userId) {
    if (firebaseAuth == null || userId == null) {
      return null;
    }
    try {
      UserRecord userRecord = firebaseAuth.getUser(userId);
      return userRecord != null ? userRecord.getDisplayName() : null;
    } catch (FirebaseAuthException e) {
      log.warn("Failed to resolve display name for user {}: {}", userId, e.getMessage());
      return null;
    }
  }

  /**
   * Helper to map nutrition map to NutritionalInfo.
   */
  private NutritionalInfo mapToNutritionalInfo(Map<String, Object> nutritionMap) {
    if (nutritionMap == null) {
      return null;
    }
    return NutritionalInfo.builder()
        .perServing(com.recipe.shared.model.NutritionValues.fromMap(nutritionMap))
        .build();
  }

  /**
   * Helper to map tips map to RecipeTips.
   */
  private com.recipe.shared.model.RecipeTips mapToRecipeTips(Map<String, List<String>> tipsMap) {
    if (tipsMap == null) {
      return null;
    }
    return com.recipe.shared.model.RecipeTips.fromMap(tipsMap);
  }
}
