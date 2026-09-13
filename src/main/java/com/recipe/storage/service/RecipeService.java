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
import com.google.cloud.firestore.SetOptions;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.recipe.shared.model.NutritionalInfo;
import com.recipe.shared.model.Recipe;
import com.recipe.storage.dto.CreateRecipeRequest;
import com.recipe.storage.dto.PagedRecipeResponse;
import com.recipe.storage.dto.RecipeResponse;
import com.recipe.storage.mapper.RecipeMapper;
import com.recipe.storage.service.RecipeAuthorService.AuthorInfo;
import com.recipe.storage.util.PaginationUtils;
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

  @Autowired(required = false)
  private NotificationService notificationService;

  @Autowired(required = false)
  private RecipeAuthorService recipeAuthorService;

  @Autowired(required = false)
  private RecipeSocialService recipeSocialService;

  @Autowired(required = false)
  private RecipeMapper recipeMapper;

  @Value("${firestore.collection.recipes}")
  private String recipesCollection;

  @Value("${firestore.collection.saved-recipes:savedRecipes}")
  private String savedRecipesCollection;

  @Value("${firestore.collection.likes:likes}")
  private String likesCollection;

  @Value("${firestore.collection.users:users}")
  private String usersCollection = "users";

  // In-memory store for testing when Firestore is not available
  private final ConcurrentHashMap<String, Recipe> mockStore = new ConcurrentHashMap<>();

  public void setRecipeAuthorService(RecipeAuthorService recipeAuthorService) {
    this.recipeAuthorService = recipeAuthorService;
  }

  public void setRecipeSocialService(RecipeSocialService recipeSocialService) {
    this.recipeSocialService = recipeSocialService;
  }

  public void setRecipeMapper(RecipeMapper recipeMapper) {
    this.recipeMapper = recipeMapper;
  }

  private RecipeAuthorService authorService() {
    if (recipeAuthorService != null) {
      return recipeAuthorService;
    }
    return new RecipeAuthorService(firestore, firebaseAuth, usersCollection);
  }

  private RecipeSocialService socialService() {
    if (recipeSocialService != null) {
      return recipeSocialService;
    }
    return new RecipeSocialService(firestore, followService, notificationService,
        authorService(), mapper(), recipesCollection, savedRecipesCollection, likesCollection);
  }

  private RecipeMapper mapper() {
    if (recipeMapper != null) {
      return recipeMapper;
    }
    return new RecipeMapper();
  }

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
          .publicRecipe(Boolean.TRUE.equals(request.getIsPublic()))
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
          .publicRecipe(Boolean.TRUE.equals(request.getIsPublic()))
          .updatedAt(now)
          .build();

      ApiFuture<WriteResult> writeFuture = docRef.set(updatedRecipe, SetOptions.merge());
      writeFuture.get();

      int existingLikeCount = extractLikeCount(document);
      RecipeResponse response = mapToResponse(updatedRecipe);
      response.setLikeCount(existingLikeCount);
      response.setLikedByCurrentUser(isRecipeLikedByUser(recipeId, userId));
      response.setSavedByCurrentUser(isRecipeSavedByUser(recipeId, userId));

      log.info("Recipe updated with ID: {} by user {}", recipeId, userId);
      return response;
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
        .publicRecipe(Boolean.TRUE.equals(request.getIsPublic()))
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
        .publicRecipe(Boolean.TRUE.equals(request.getIsPublic()))
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
    return getPublicRecipes(pageToken, size, null);
  }

  /**
   * Get public recipes with cursor-based pagination, optionally populating like and save
   * status for the authenticated user.
   *
   * @param pageToken Opaque cursor token from a previous response (null for first page)
   * @param size Number of recipes per page (min 1, max 100)
   * @param userId The optional Firebase UID of the requesting user
   * @return Paginated list of public recipes
   */
  public PagedRecipeResponse getPublicRecipes(String pageToken, int size, String userId) {
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
      Map<String, AuthorInfo> authorCache = new HashMap<>();
      querySnapshot.getDocuments().forEach(doc -> {
        Recipe recipe = doc.toObject(Recipe.class);
        RecipeResponse response = mapToResponse(recipe);
        response.setLikeCount(extractLikeCount(doc));
        String uid = recipe.getUserId();
        if (uid != null) {
          AuthorInfo authorInfo = authorCache.computeIfAbsent(uid, this::resolveAuthorInfo);
          response.setAuthorDisplayName(authorInfo.displayName());
          response.setAuthorAvatarUrl(authorInfo.avatarUrl());
        }
        recipes.add(response);
      });

      populateLikeAndSaveStatuses(recipes, userId);

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
    return socialService().getFeed(userId, pageToken, size);
  }

  private static <T> List<List<T>> partitionList(List<T> list, int batchSize) {
    return PaginationUtils.partitionList(list, batchSize);
  }

  /**
   * Decodes an opaque page token into a Firestore {@link Timestamp} cursor.
   *
   * @param pageToken the opaque cursor token from a previous paged response
   * @return the decoded Firestore Timestamp to pass to {@code startAfter()}
   */
  private Timestamp decodePageToken(String pageToken) {
    return PaginationUtils.decodePageToken(pageToken);
  }

  /**
   * Encodes the {@code createdAt} timestamp of the last document in a query snapshot
   * into an opaque page token for cursor-based pagination.
   *
   * @param querySnapshot the Firestore query result snapshot
   * @return a URL-safe base64 cursor token, or {@code null} if no next page exists
   */
  private String encodeNextPageToken(QuerySnapshot querySnapshot) {
    return PaginationUtils.encodeNextPageTokenFromField(querySnapshot, "createdAt");
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
    return PaginationUtils.encodeNextPageTokenFromField(querySnapshot, fieldName);
  }

  /**
   * Get a public recipe by ID without requiring authentication.
   * Returns 404 if the recipe does not exist or is not public.
   *
   * @param recipeId The recipe ID
   * @return The recipe if it exists and is public
   * @throws ResponseStatusException 404 if not found or not public
   */
  public RecipeResponse getPublicRecipe(String recipeId) {
    return getPublicRecipe(recipeId, null);
  }

  /**
   * Get a public recipe by ID, optionally populating like and save status
   * for the authenticated user.
   * Returns 404 if the recipe does not exist or is not public.
   *
   * @param recipeId The recipe ID
   * @param userId The optional Firebase user ID
   * @return The recipe if it exists and is public
   * @throws ResponseStatusException 404 if not found or not public
   */
  public RecipeResponse getPublicRecipe(String recipeId, String userId) {
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
      AuthorInfo authorInfo = resolveAuthorInfo(recipe.getUserId());
      response.setAuthorDisplayName(authorInfo.displayName());
      response.setAuthorAvatarUrl(authorInfo.avatarUrl());
      response.setLikeCount(extractLikeCount(document));
      if (userId != null) {
        response.setLikedByCurrentUser(isRecipeLikedByUser(recipeId, userId));
        response.setSavedByCurrentUser(isRecipeSavedByUser(recipeId, userId));
      }
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
   * Save (bookmark) a recipe for a user.
   *
   * @param recipeId The recipe ID to save
   * @param userId   The Firebase user ID
   */
  public void saveRecipeForUser(String recipeId, String userId) {
    socialService().saveRecipeForUser(recipeId, userId);
  }

  /**
   * Unsave (remove bookmark) a recipe for a user.
   *
   * @param recipeId The recipe ID to unsave
   * @param userId   The Firebase user ID
   */
  public void unsaveRecipeForUser(String recipeId, String userId) {
    socialService().unsaveRecipeForUser(recipeId, userId);
  }

  /**
   * Like a recipe for a user.
   *
   * @param recipeId The recipe ID to like
   * @param userId   The Firebase user ID
   */
  public void likeRecipe(String recipeId, String userId) {
    socialService().likeRecipe(recipeId, userId);
  }

  /**
   * Unlike a recipe for a user.
   *
   * @param recipeId The recipe ID to unlike
   * @param userId   The Firebase user ID
   */
  public void unlikeRecipe(String recipeId, String userId) {
    socialService().unlikeRecipe(recipeId, userId);
  }

  /**
   * Get paginated saved recipes for a user.
   *
   * @param userId    The Firebase user ID
   * @param pageToken Opaque cursor token from a previous response (null for first page)
   * @param size      Number of recipes per page (min 1, max 100)
   * @return Paginated list of saved recipes
   */
  public PagedRecipeResponse getSavedRecipes(String userId, String pageToken, int size) {
    return socialService().getSavedRecipes(userId, pageToken, size);
  }

  boolean isRecipeLikedByUser(String recipeId, String userId) {
    return socialService().isRecipeLikedByUser(recipeId, userId);
  }

  int extractLikeCount(DocumentSnapshot doc) {
    return socialService().extractLikeCount(doc);
  }

  boolean isRecipeSavedByUser(String recipeId, String userId) {
    return socialService().isRecipeSavedByUser(recipeId, userId);
  }

  void populateLikeAndSaveStatuses(List<RecipeResponse> recipes, String userId) {
    socialService().populateLikeAndSaveStatuses(recipes, userId);
  }

  /**
   * Map Recipe entity to RecipeResponse DTO.
   */
  RecipeResponse mapToResponse(Recipe recipe) {
    return mapper().mapToResponse(recipe);
  }

  /**
   * Helper to resolve author metadata from Firestore profile or Firebase Auth.
   *
   * @param userId The user ID
   * @return The resolved author information
   */
  AuthorInfo resolveAuthorInfo(String userId) {
    return authorService().resolveAuthorInfo(userId);
  }

  /**
   * Resolve a user's display name from Firestore profile or Firebase Auth.
   *
   * @param userId The Firebase user ID
   * @return The display name, or null if lookup fails or auth is unavailable
   */
  String resolveDisplayName(String userId) {
    return authorService().resolveDisplayName(userId);
  }

  /**
   * Helper to resolve the avatar URL for a user from Firestore profile or Firebase Auth.
   *
   * @param userId The Firebase user ID
   * @return The avatar URL, or null if not set or lookup fails
   */
  String resolveAvatarUrl(String userId) {
    return authorService().resolveAvatarUrl(userId);
  }

  /**
   * Helper to map nutrition map to NutritionalInfo.
   */
  private NutritionalInfo mapToNutritionalInfo(Map<String, Object> nutritionMap) {
    return mapper().mapToNutritionalInfo(nutritionMap);
  }

  /**
   * Helper to map tips map to RecipeTips.
   */
  private com.recipe.shared.model.RecipeTips mapToRecipeTips(Map<String, List<String>> tipsMap) {
    return mapper().mapToRecipeTips(tipsMap);
  }
}
