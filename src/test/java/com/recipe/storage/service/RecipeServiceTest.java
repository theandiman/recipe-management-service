package com.recipe.storage.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.AggregateQuery;
import com.google.cloud.firestore.AggregateQuerySnapshot;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.recipe.storage.dto.CreateRecipeRequest;
import com.recipe.storage.dto.PagedRecipeResponse;
import com.recipe.storage.dto.RecipeResponse;
import com.recipe.shared.model.Recipe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecipeServiceTest {

        @Mock
        private Firestore firestore;

        @Mock
        private DocumentReference documentReference;

        @Mock
        private ApiFuture<WriteResult> writeResultFuture;

        @Mock
        private WriteResult writeResult;

    @Mock
    private FirebaseAuth firebaseAuth;

    @Mock
    private FollowService followService;

    private RecipeService recipeService;

    @BeforeEach
    void setUp() {
        recipeService = new RecipeService();
        ReflectionTestUtils.setField(recipeService, "firestore", firestore);
        ReflectionTestUtils.setField(recipeService, "recipesCollection", "recipes");
        ReflectionTestUtils.setField(recipeService, "savedRecipesCollection", "savedRecipes");
        ReflectionTestUtils.setField(recipeService, "likesCollection", "likes");
        ReflectionTestUtils.setField(recipeService, "firebaseAuth", firebaseAuth);
        ReflectionTestUtils.setField(recipeService, "followService", followService);
    }

        @Test
        void saveRecipe_WithFirestore_Success() throws ExecutionException, InterruptedException {
                // Arrange
                CreateRecipeRequest request = createValidRequest();
                String userId = "user123";

                com.google.cloud.firestore.CollectionReference collectionRef = mock(
                                com.google.cloud.firestore.CollectionReference.class);
                when(firestore.collection(anyString())).thenReturn(collectionRef);
                when(collectionRef.document(anyString())).thenReturn(documentReference);
                when(documentReference.set(any(Recipe.class))).thenReturn(writeResultFuture);
                when(writeResultFuture.get()).thenReturn(writeResult);

                // Act
                RecipeResponse response = recipeService.saveRecipe(request, userId);

                // Assert
                assertNotNull(response);
                assertEquals(request.getTitle(), response.getTitle());
                assertEquals(userId, response.getUserId());
                assertEquals(request.getSource(), response.getSource());
                verify(firestore).collection("recipes");
                verify(documentReference).set(any(Recipe.class));
        }

        @Test
        void saveRecipe_WithoutFirestore_ReturnsMockResponse() {
                // Arrange
                RecipeService serviceWithoutFirestore = new RecipeService();
                ReflectionTestUtils.setField(serviceWithoutFirestore, "recipesCollection", "recipes");
                CreateRecipeRequest request = createValidRequest();
                String userId = "user123";

                // Act
                RecipeResponse response = serviceWithoutFirestore.saveRecipe(request, userId);

                // Assert
                assertNotNull(response);
                assertNotNull(response.getId());
                assertEquals(request.getTitle(), response.getTitle());
                assertEquals(userId, response.getUserId());
                // Source comes from request, not hardcoded to "test-mode"
                assertEquals(request.getSource(), response.getSource());
        }

        @Test
        void saveRecipe_ValidatesRequiredFields() throws ExecutionException, InterruptedException {
                // Arrange
                CreateRecipeRequest request = createValidRequest();
                String userId = "user123";

                com.google.cloud.firestore.CollectionReference collectionRef = mock(
                                com.google.cloud.firestore.CollectionReference.class);
                when(firestore.collection(anyString())).thenReturn(collectionRef);
                when(collectionRef.document(anyString())).thenReturn(documentReference);
                when(documentReference.set(any(Recipe.class))).thenReturn(writeResultFuture);
                when(writeResultFuture.get()).thenReturn(writeResult);

                // Act
                RecipeResponse response = recipeService.saveRecipe(request, userId);

                // Assert
                assertNotNull(response.getTitle());
                assertNotNull(response.getIngredients());
                assertNotNull(response.getInstructions());
                assertNotNull(response.getServings());
        }

        @Test
        void updateRecipe_WithFirestore_Success() throws ExecutionException, InterruptedException {
                // Arrange
                String recipeId = "recipe123";
                String userId = "user123";
                CreateRecipeRequest request = createValidRequest();
                request.setTitle("Updated Title");

                com.google.cloud.firestore.CollectionReference collectionRef = mock(
                                com.google.cloud.firestore.CollectionReference.class);
                when(firestore.collection(anyString())).thenReturn(collectionRef);
                when(collectionRef.document(recipeId)).thenReturn(documentReference);

                ApiFuture<com.google.cloud.firestore.DocumentSnapshot> futureSnapshot = mock(ApiFuture.class);
                com.google.cloud.firestore.DocumentSnapshot documentSnapshot = mock(
                                com.google.cloud.firestore.DocumentSnapshot.class);
                when(documentReference.get()).thenReturn(futureSnapshot);
                when(futureSnapshot.get()).thenReturn(documentSnapshot);
                when(documentSnapshot.exists()).thenReturn(true);

                com.recipe.shared.model.Recipe sharedRecipe = com.recipe.shared.model.Recipe.builder()
                                .id(recipeId)
                                .userId(userId)
                                .recipeName("Old Title")
                                .build();
                Recipe existingRecipe = sharedRecipe;
                when(documentSnapshot.toObject(Recipe.class)).thenReturn(existingRecipe);

                when(documentReference.set(any(Recipe.class))).thenReturn(writeResultFuture);
                when(writeResultFuture.get()).thenReturn(writeResult);

                // Act
                RecipeResponse response = recipeService.updateRecipe(recipeId, request, userId);

                // Assert
                assertNotNull(response);
                assertEquals("Updated Title", response.getTitle());
                verify(documentReference).set(any(Recipe.class));
        }

        @Test
        void updateRecipe_RecipeNotFound_ThrowsNotFoundException() throws ExecutionException, InterruptedException {
                // Arrange
                String recipeId = "nonexistent123";
                String userId = "user123";
                CreateRecipeRequest request = createValidRequest();

                com.google.cloud.firestore.CollectionReference collectionRef = mock(
                                com.google.cloud.firestore.CollectionReference.class);
                when(firestore.collection(anyString())).thenReturn(collectionRef);
                when(collectionRef.document(recipeId)).thenReturn(documentReference);

                ApiFuture<com.google.cloud.firestore.DocumentSnapshot> futureSnapshot = mock(ApiFuture.class);
                com.google.cloud.firestore.DocumentSnapshot documentSnapshot = mock(
                                com.google.cloud.firestore.DocumentSnapshot.class);
                when(documentReference.get()).thenReturn(futureSnapshot);
                when(futureSnapshot.get()).thenReturn(documentSnapshot);
                when(documentSnapshot.exists()).thenReturn(false);

                // Act & Assert
                assertThrows(org.springframework.web.server.ResponseStatusException.class,
                                () -> recipeService.updateRecipe(recipeId, request, userId));
        }

        @Test
        void updateRecipe_UserDoesNotOwnRecipe_ThrowsForbiddenException()
                        throws ExecutionException, InterruptedException {
                // Arrange
                String recipeId = "recipe123";
                String userId = "user123";
                String differentUserId = "user456";
                CreateRecipeRequest request = createValidRequest();

                com.google.cloud.firestore.CollectionReference collectionRef = mock(
                                com.google.cloud.firestore.CollectionReference.class);
                when(firestore.collection(anyString())).thenReturn(collectionRef);
                when(collectionRef.document(recipeId)).thenReturn(documentReference);

                ApiFuture<com.google.cloud.firestore.DocumentSnapshot> futureSnapshot = mock(ApiFuture.class);
                com.google.cloud.firestore.DocumentSnapshot documentSnapshot = mock(
                                com.google.cloud.firestore.DocumentSnapshot.class);
                when(documentReference.get()).thenReturn(futureSnapshot);
                when(futureSnapshot.get()).thenReturn(documentSnapshot);
                when(documentSnapshot.exists()).thenReturn(true);

                com.recipe.shared.model.Recipe sharedRecipe = com.recipe.shared.model.Recipe.builder()
                                .id(recipeId)
                                .userId(differentUserId)
                                .recipeName("Some Recipe")
                                .build();
                Recipe existingRecipe = sharedRecipe;
                when(documentSnapshot.toObject(Recipe.class)).thenReturn(existingRecipe);

                // Act & Assert
                assertThrows(org.springframework.web.server.ResponseStatusException.class,
                                () -> recipeService.updateRecipe(recipeId, request, userId));
        }

        @Test
        void updateRecipe_DeserializationFailure_ThrowsInternalServerError()
                        throws ExecutionException, InterruptedException {
                // Arrange
                String recipeId = "recipe123";
                String userId = "user123";
                CreateRecipeRequest request = createValidRequest();

                com.google.cloud.firestore.CollectionReference collectionRef = mock(
                                com.google.cloud.firestore.CollectionReference.class);
                when(firestore.collection(anyString())).thenReturn(collectionRef);
                when(collectionRef.document(recipeId)).thenReturn(documentReference);

                ApiFuture<com.google.cloud.firestore.DocumentSnapshot> futureSnapshot = mock(ApiFuture.class);
                com.google.cloud.firestore.DocumentSnapshot documentSnapshot = mock(
                                com.google.cloud.firestore.DocumentSnapshot.class);
                when(documentReference.get()).thenReturn(futureSnapshot);
                when(futureSnapshot.get()).thenReturn(documentSnapshot);
                when(documentSnapshot.exists()).thenReturn(true);
                when(documentSnapshot.toObject(Recipe.class)).thenReturn(null);

                // Act & Assert
                assertThrows(org.springframework.web.server.ResponseStatusException.class,
                                () -> recipeService.updateRecipe(recipeId, request, userId));
        }

        @Test
        void updateRecipeSharing_Success() throws ExecutionException, InterruptedException {
                // Arrange
                String recipeId = "recipe123";
                String userId = "user123";
                boolean isPublic = true;

                com.google.cloud.firestore.CollectionReference collectionRef = mock(
                                com.google.cloud.firestore.CollectionReference.class);
                when(firestore.collection(anyString())).thenReturn(collectionRef);
                when(collectionRef.document(recipeId)).thenReturn(documentReference);

                ApiFuture<com.google.cloud.firestore.DocumentSnapshot> futureSnapshot = mock(ApiFuture.class);
                com.google.cloud.firestore.DocumentSnapshot documentSnapshot = mock(
                                com.google.cloud.firestore.DocumentSnapshot.class);
                when(documentReference.get()).thenReturn(futureSnapshot);
                when(futureSnapshot.get()).thenReturn(documentSnapshot);
                when(documentSnapshot.exists()).thenReturn(true);

                Recipe existingRecipe = Recipe.builder()
                                .id(recipeId)
                                .userId(userId)
                                .recipeName("Test Recipe")
                                .publicRecipe(false)
                                .build();
                when(documentSnapshot.toObject(Recipe.class)).thenReturn(existingRecipe);

                when(documentReference.update(eq("isPublic"), eq(isPublic), eq("updatedAt"), any()))
                                .thenReturn(writeResultFuture);
                when(writeResultFuture.get()).thenReturn(writeResult);

                // Act
                RecipeResponse response = recipeService.updateRecipeSharing(recipeId, isPublic, userId);

                // Assert
                assertNotNull(response);
                assertTrue(response.isPublic());
                verify(documentReference).update(eq("isPublic"), eq(isPublic), eq("updatedAt"), any());
        }

        @Test
        void updateRecipeSharing_UserDoesNotOwnRecipe_ThrowsForbidden()
                        throws ExecutionException, InterruptedException {
                // Arrange
                String recipeId = "recipe123";
                String userId = "user123";
                String differentUserId = "user456";
                boolean isPublic = true;

                com.google.cloud.firestore.CollectionReference collectionRef = mock(
                                com.google.cloud.firestore.CollectionReference.class);
                when(firestore.collection(anyString())).thenReturn(collectionRef);
                when(collectionRef.document(recipeId)).thenReturn(documentReference);

                ApiFuture<com.google.cloud.firestore.DocumentSnapshot> futureSnapshot = mock(ApiFuture.class);
                com.google.cloud.firestore.DocumentSnapshot documentSnapshot = mock(
                                com.google.cloud.firestore.DocumentSnapshot.class);
                when(documentReference.get()).thenReturn(futureSnapshot);
                when(futureSnapshot.get()).thenReturn(documentSnapshot);
                when(documentSnapshot.exists()).thenReturn(true);

                Recipe existingRecipe = Recipe.builder()
                                .id(recipeId)
                                .userId(differentUserId)
                                .recipeName("Test Recipe")
                                .publicRecipe(false)
                                .build();
                when(documentSnapshot.toObject(Recipe.class)).thenReturn(existingRecipe);

                // Act & Assert
                org.springframework.web.server.ResponseStatusException exception = assertThrows(
                                org.springframework.web.server.ResponseStatusException.class,
                                () -> recipeService.updateRecipeSharing(recipeId, isPublic, userId));
                assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, exception.getStatusCode());
        }

        @Test
        void updateRecipeSharing_RecipeNotFound_ThrowsNotFoundException()
                        throws ExecutionException, InterruptedException {
                // Arrange
                String recipeId = "nonexistent123";
                String userId = "user123";
                boolean isPublic = true;

                com.google.cloud.firestore.CollectionReference collectionRef = mock(
                                com.google.cloud.firestore.CollectionReference.class);
                when(firestore.collection(anyString())).thenReturn(collectionRef);
                when(collectionRef.document(recipeId)).thenReturn(documentReference);

                ApiFuture<com.google.cloud.firestore.DocumentSnapshot> futureSnapshot = mock(ApiFuture.class);
                com.google.cloud.firestore.DocumentSnapshot documentSnapshot = mock(
                                com.google.cloud.firestore.DocumentSnapshot.class);
                when(documentReference.get()).thenReturn(futureSnapshot);
                when(futureSnapshot.get()).thenReturn(documentSnapshot);
                when(documentSnapshot.exists()).thenReturn(false);

                // Act & Assert
                org.springframework.web.server.ResponseStatusException exception = assertThrows(
                                org.springframework.web.server.ResponseStatusException.class,
                                () -> recipeService.updateRecipeSharing(recipeId, isPublic, userId));
                assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, exception.getStatusCode());
        }

        @Test
        void updateRecipeSharing_FirestoreNotConfigured_UpdatesMockStore() {
                // Arrange
                RecipeService serviceWithoutFirestore = new RecipeService();
                ReflectionTestUtils.setField(serviceWithoutFirestore, "recipesCollection", "recipes");
                String userId = "user123";
                CreateRecipeRequest request = createValidRequest();

                // Save a recipe to mock store first
                RecipeResponse savedRecipe = serviceWithoutFirestore.saveRecipe(request, userId);
                String recipeId = savedRecipe.getId();
                boolean isPublic = true;

                // Act
                RecipeResponse response = serviceWithoutFirestore.updateRecipeSharing(recipeId, isPublic, userId);

                // Assert
                assertNotNull(response);
                assertTrue(response.isPublic());
                assertEquals(recipeId, response.getId());
        }

    @Test
    void getPublicRecipes_NoFirestore_ReturnsEmptyPagedResponse() {
        // Arrange
        RecipeService serviceWithoutFirestore = new RecipeService();
        ReflectionTestUtils.setField(serviceWithoutFirestore, "recipesCollection", "recipes");

        // Act
        PagedRecipeResponse response = serviceWithoutFirestore.getPublicRecipes(null, 20);

        // Assert
        assertNotNull(response);
        assertNotNull(response.getRecipes());
        assertTrue(response.getRecipes().isEmpty());
        assertEquals(20, response.getSize());
        assertEquals(0, response.getTotalCount());
        assertNull(response.getNextPageToken());
    }

    @Test
    void getPublicRecipes_SizeExceedsMax_ThrowsBadRequest() {
        // Arrange
        RecipeService serviceWithoutFirestore = new RecipeService();
        ReflectionTestUtils.setField(serviceWithoutFirestore, "recipesCollection", "recipes");

        // Act & Assert
        org.springframework.web.server.ResponseStatusException exception = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> serviceWithoutFirestore.getPublicRecipes(null, 101));
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void getPublicRecipes_ZeroSize_ThrowsBadRequest() {
        // Arrange
        RecipeService serviceWithoutFirestore = new RecipeService();
        ReflectionTestUtils.setField(serviceWithoutFirestore, "recipesCollection", "recipes");

        // Act & Assert
        org.springframework.web.server.ResponseStatusException exception = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> serviceWithoutFirestore.getPublicRecipes(null, 0));
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void getPublicRecipes_NegativeSize_ThrowsBadRequest() {
        // Arrange
        RecipeService serviceWithoutFirestore = new RecipeService();
        ReflectionTestUtils.setField(serviceWithoutFirestore, "recipesCollection", "recipes");

        // Act & Assert
        org.springframework.web.server.ResponseStatusException exception = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> serviceWithoutFirestore.getPublicRecipes(null, -5));
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void getPublicRecipes_SizeAtMax_DoesNotThrow() {
        // Arrange
        RecipeService serviceWithoutFirestore = new RecipeService();
        ReflectionTestUtils.setField(serviceWithoutFirestore, "recipesCollection", "recipes");

        // Act & Assert - size == 100 should not throw
        PagedRecipeResponse response = serviceWithoutFirestore.getPublicRecipes(null, 100);
        assertNotNull(response);
        assertEquals(100, response.getSize());
    }

    @Test
    void getPublicRecipes_InvalidPageToken_ThrowsBadRequest() {
        // Arrange - token is validated before any Firestore call, so no Firestore needed
        RecipeService serviceWithoutFirestore = new RecipeService();
        ReflectionTestUtils.setField(serviceWithoutFirestore, "recipesCollection", "recipes");

        // Act & Assert
        org.springframework.web.server.ResponseStatusException exception = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> serviceWithoutFirestore.getPublicRecipes("not-valid-base64!!!", 10));
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void getPublicRecipe_PopulatesAuthorDisplayName() throws Exception {
        // Arrange
        String recipeId = "recipe123";
        String userId = "user123";
        String displayName = "John Doe";

        com.google.cloud.firestore.CollectionReference collectionRef = mock(
                com.google.cloud.firestore.CollectionReference.class);
        when(firestore.collection(anyString())).thenReturn(collectionRef);
        when(collectionRef.document(recipeId)).thenReturn(documentReference);

        ApiFuture<com.google.cloud.firestore.DocumentSnapshot> futureSnapshot = mock(ApiFuture.class);
        com.google.cloud.firestore.DocumentSnapshot documentSnapshot = mock(
                com.google.cloud.firestore.DocumentSnapshot.class);
        when(documentReference.get()).thenReturn(futureSnapshot);
        when(futureSnapshot.get()).thenReturn(documentSnapshot);
        when(documentSnapshot.exists()).thenReturn(true);

        Recipe recipe = Recipe.builder()
                .id(recipeId)
                .userId(userId)
                .recipeName("Public Recipe")
                .publicRecipe(true)
                .createdAt(Instant.now())
                .build();
        when(documentSnapshot.toObject(Recipe.class)).thenReturn(recipe);

        UserRecord userRecord = mock(UserRecord.class);
        when(firebaseAuth.getUser(userId)).thenReturn(userRecord);
        when(userRecord.getDisplayName()).thenReturn(displayName);

        // Act
        RecipeResponse response = recipeService.getPublicRecipe(recipeId);

        // Assert
        assertNotNull(response);
        assertEquals(recipeId, response.getId());
        assertEquals(displayName, response.getAuthorDisplayName());
        assertTrue(response.isPublic());
    }

    @Test
    void getPublicRecipe_FirebaseLookupFails_ReturnsNullDisplayName() throws Exception {
        // Arrange
        String recipeId = "recipe123";
        String userId = "user123";

        CollectionReference collectionRef = mock(CollectionReference.class);
        when(firestore.collection(anyString())).thenReturn(collectionRef);
        when(collectionRef.document(recipeId)).thenReturn(documentReference);

        ApiFuture<com.google.cloud.firestore.DocumentSnapshot> futureSnapshot = mock(ApiFuture.class);
        com.google.cloud.firestore.DocumentSnapshot documentSnapshot =
                mock(com.google.cloud.firestore.DocumentSnapshot.class);
        when(documentReference.get()).thenReturn(futureSnapshot);
        when(futureSnapshot.get()).thenReturn(documentSnapshot);
        when(documentSnapshot.exists()).thenReturn(true);

        Recipe recipe = Recipe.builder()
                .id(recipeId)
                .userId(userId)
                .recipeName("Public Recipe")
                .publicRecipe(true)
                .createdAt(Instant.now())
                .build();
        when(documentSnapshot.toObject(Recipe.class)).thenReturn(recipe);

        FirebaseAuthException authException = mock(FirebaseAuthException.class);
        when(firebaseAuth.getUser(userId)).thenThrow(authException);

        // Act
        RecipeResponse response = recipeService.getPublicRecipe(recipeId);

        // Assert
        assertNotNull(response);
        assertNull(response.getAuthorDisplayName());
    }

    @Test
    void getPublicRecipe_PrivateRecipe_ThrowsNotFoundException() throws ExecutionException, InterruptedException {
        // Arrange
        String recipeId = "recipe123";

        com.google.cloud.firestore.CollectionReference collectionRef = mock(
                com.google.cloud.firestore.CollectionReference.class);
        when(firestore.collection(anyString())).thenReturn(collectionRef);
        when(collectionRef.document(recipeId)).thenReturn(documentReference);

        ApiFuture<com.google.cloud.firestore.DocumentSnapshot> futureSnapshot = mock(ApiFuture.class);
        com.google.cloud.firestore.DocumentSnapshot documentSnapshot = mock(
                com.google.cloud.firestore.DocumentSnapshot.class);
        when(documentReference.get()).thenReturn(futureSnapshot);
        when(futureSnapshot.get()).thenReturn(documentSnapshot);
        when(documentSnapshot.exists()).thenReturn(true);

        Recipe recipe = Recipe.builder()
                .id(recipeId)
                .userId("owner123")
                .recipeName("Private Recipe")
                .publicRecipe(false)
                .build();
        when(documentSnapshot.toObject(Recipe.class)).thenReturn(recipe);

        // Act & Assert
        org.springframework.web.server.ResponseStatusException exception = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> recipeService.getPublicRecipe(recipeId));
        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void getPublicRecipe_NonExistentRecipe_ThrowsNotFoundException() throws ExecutionException, InterruptedException {
        // Arrange
        String recipeId = "nonexistent123";

        com.google.cloud.firestore.CollectionReference collectionRef = mock(
                com.google.cloud.firestore.CollectionReference.class);
        when(firestore.collection(anyString())).thenReturn(collectionRef);
        when(collectionRef.document(recipeId)).thenReturn(documentReference);

        ApiFuture<com.google.cloud.firestore.DocumentSnapshot> futureSnapshot = mock(ApiFuture.class);
        com.google.cloud.firestore.DocumentSnapshot documentSnapshot = mock(
                com.google.cloud.firestore.DocumentSnapshot.class);
        when(documentReference.get()).thenReturn(futureSnapshot);
        when(futureSnapshot.get()).thenReturn(documentSnapshot);
        when(documentSnapshot.exists()).thenReturn(false);

        // Act & Assert
        org.springframework.web.server.ResponseStatusException exception = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> recipeService.getPublicRecipe(recipeId));
        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void getPublicRecipe_FirestoreNotConfigured_ThrowsServiceUnavailable() {
        // Arrange
        RecipeService serviceWithoutFirestore = new RecipeService();
        ReflectionTestUtils.setField(serviceWithoutFirestore, "recipesCollection", "recipes");
        String recipeId = "recipe123";

        // Act & Assert
        org.springframework.web.server.ResponseStatusException exception = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> serviceWithoutFirestore.getPublicRecipe(recipeId));
        assertEquals(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
    }

    @Test
    void getPublicRecipes_WithFirestore_ReturnsPagedResponse()
            throws ExecutionException, InterruptedException {
        // Arrange: mock collection and base where-query
        CollectionReference collectionRef = mock(CollectionReference.class);
        when(firestore.collection(anyString())).thenReturn(collectionRef);
        Query baseQuery = mock(Query.class);
        when(collectionRef.whereEqualTo("isPublic", true)).thenReturn(baseQuery);

        // Mock count aggregation
        AggregateQuery aggregateQuery = mock(AggregateQuery.class);
        when(baseQuery.count()).thenReturn(aggregateQuery);
        @SuppressWarnings("unchecked")
        ApiFuture<AggregateQuerySnapshot> countFuture = mock(ApiFuture.class);
        when(aggregateQuery.get()).thenReturn(countFuture);
        AggregateQuerySnapshot countSnapshot = mock(AggregateQuerySnapshot.class);
        when(countFuture.get()).thenReturn(countSnapshot);
        when(countSnapshot.getCount()).thenReturn(1L);

        // Mock orderBy/limit chain
        Query orderedQuery = mock(Query.class);
        when(baseQuery.orderBy("createdAt", Query.Direction.DESCENDING)).thenReturn(orderedQuery);
        Query limitedQuery = mock(Query.class);
        when(orderedQuery.limit(20)).thenReturn(limitedQuery);

        // Mock query execution with one document result
        @SuppressWarnings("unchecked")
        ApiFuture<QuerySnapshot> queryFuture = mock(ApiFuture.class);
        when(limitedQuery.get()).thenReturn(queryFuture);
        QuerySnapshot querySnapshot = mock(QuerySnapshot.class);
        when(queryFuture.get()).thenReturn(querySnapshot);

        QueryDocumentSnapshot docSnapshot = mock(QueryDocumentSnapshot.class);
        Recipe recipe = Recipe.builder()
                .id("id1").userId("user1").recipeName("Pasta").publicRecipe(true).build();
        when(docSnapshot.toObject(Recipe.class)).thenReturn(recipe);
        Timestamp ts = Timestamp.ofTimeSecondsAndNanos(1743000000L, 0);
        when(docSnapshot.getTimestamp("createdAt")).thenReturn(ts);

        List<QueryDocumentSnapshot> docs = List.of(docSnapshot);
        when(querySnapshot.getDocuments()).thenReturn(docs);
        when(querySnapshot.isEmpty()).thenReturn(false);

        // Act
        PagedRecipeResponse response = recipeService.getPublicRecipes(null, 20);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getRecipes().size());
        assertEquals(1L, response.getTotalCount());
        assertEquals(20, response.getSize());
        assertNotNull(response.getNextPageToken()); // last doc has createdAt → token encoded
    }

    @Test
    void getPublicRecipes_WithValidPageToken_StartsAfterCursor()
            throws ExecutionException, InterruptedException {
        // Build a valid token: URL-safe base64 of "1743000000,0"
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("1743000000,0".getBytes(StandardCharsets.UTF_8));

        // Arrange: mock collection and base where-query
        CollectionReference collectionRef = mock(CollectionReference.class);
        when(firestore.collection(anyString())).thenReturn(collectionRef);
        Query baseQuery = mock(Query.class);
        when(collectionRef.whereEqualTo("isPublic", true)).thenReturn(baseQuery);

        // Mock count aggregation
        AggregateQuery aggregateQuery = mock(AggregateQuery.class);
        when(baseQuery.count()).thenReturn(aggregateQuery);
        @SuppressWarnings("unchecked")
        ApiFuture<AggregateQuerySnapshot> countFuture = mock(ApiFuture.class);
        when(aggregateQuery.get()).thenReturn(countFuture);
        AggregateQuerySnapshot countSnapshot = mock(AggregateQuerySnapshot.class);
        when(countFuture.get()).thenReturn(countSnapshot);
        when(countSnapshot.getCount()).thenReturn(5L);

        // Mock orderBy → startAfter → limit chain
        Query orderedQuery = mock(Query.class);
        when(baseQuery.orderBy("createdAt", Query.Direction.DESCENDING)).thenReturn(orderedQuery);
        Query afterQuery = mock(Query.class);
        when(orderedQuery.startAfter(any(Timestamp.class))).thenReturn(afterQuery);
        Query limitedQuery = mock(Query.class);
        when(afterQuery.limit(10)).thenReturn(limitedQuery);

        // Mock empty result (no next page)
        @SuppressWarnings("unchecked")
        ApiFuture<QuerySnapshot> queryFuture = mock(ApiFuture.class);
        when(limitedQuery.get()).thenReturn(queryFuture);
        QuerySnapshot querySnapshot = mock(QuerySnapshot.class);
        when(queryFuture.get()).thenReturn(querySnapshot);
        when(querySnapshot.getDocuments()).thenReturn(List.of());
        when(querySnapshot.isEmpty()).thenReturn(true);

        // Act
        PagedRecipeResponse response = recipeService.getPublicRecipes(token, 10);

        // Assert
        assertNotNull(response);
        assertTrue(response.getRecipes().isEmpty());
        assertEquals(5L, response.getTotalCount());
        assertNull(response.getNextPageToken()); // empty result → no next page
        verify(orderedQuery).startAfter(any(Timestamp.class));
    }

    @Test
    void getPublicRecipe_NullDocument_ThrowsNotFoundException()
            throws ExecutionException, InterruptedException {
        // Arrange: future.get() returns null
        String recipeId = "recipe123";
        CollectionReference collectionRef = mock(CollectionReference.class);
        when(firestore.collection(anyString())).thenReturn(collectionRef);
        when(collectionRef.document(recipeId)).thenReturn(documentReference);

        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> futureSnapshot = mock(ApiFuture.class);
        when(documentReference.get()).thenReturn(futureSnapshot);
        when(futureSnapshot.get()).thenReturn(null);

        // Act & Assert
        org.springframework.web.server.ResponseStatusException exception = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> recipeService.getPublicRecipe(recipeId));
        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void getPublicRecipe_NullRecipeDeserialization_ThrowsNotFoundException()
            throws ExecutionException, InterruptedException {
        // Arrange: document exists but toObject returns null
        String recipeId = "recipe123";
        CollectionReference collectionRef = mock(CollectionReference.class);
        when(firestore.collection(anyString())).thenReturn(collectionRef);
        when(collectionRef.document(recipeId)).thenReturn(documentReference);

        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> futureSnapshot = mock(ApiFuture.class);
        DocumentSnapshot documentSnapshot = mock(DocumentSnapshot.class);
        when(documentReference.get()).thenReturn(futureSnapshot);
        when(futureSnapshot.get()).thenReturn(documentSnapshot);
        when(documentSnapshot.exists()).thenReturn(true);
        when(documentSnapshot.toObject(Recipe.class)).thenReturn(null);

        // Act & Assert
        org.springframework.web.server.ResponseStatusException exception = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> recipeService.getPublicRecipe(recipeId));
        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    private CreateRecipeRequest createValidRequest() {
        return CreateRecipeRequest.builder()
                .title("Delicious Pasta")
                .description("A wonderful Italian pasta dish")
                .ingredients(List.of("200g pasta", "2 tomatoes", "1 onion"))
                .instructions(List.of("Boil water", "Cook pasta", "Mix ingredients"))
                .prepTime(15)
                .cookTime(20)
                .servings(4)
                .source("ai-generated")
                .build();
    }

    // ── getUserRecipes ───────────────────────────────────────────────────────

    @Test
    void getUserRecipes_WithFirestore_PopulatesSavedStatus()
            throws ExecutionException, InterruptedException {
        // Arrange
        String userId = "user123";
        String recipeId = "recipe123";

        // Mock recipe collection query
        CollectionReference recipesRef = mock(CollectionReference.class);
        when(firestore.collection("recipes")).thenReturn(recipesRef);
        Query userQuery = mock(Query.class);
        when(recipesRef.whereEqualTo("userId", userId)).thenReturn(userQuery);

        @SuppressWarnings("unchecked")
        ApiFuture<QuerySnapshot> queryFuture = mock(ApiFuture.class);
        QuerySnapshot querySnapshot = mock(QuerySnapshot.class);
        when(userQuery.get()).thenReturn(queryFuture);
        when(queryFuture.get()).thenReturn(querySnapshot);

        com.google.cloud.firestore.QueryDocumentSnapshot docSnap =
                mock(com.google.cloud.firestore.QueryDocumentSnapshot.class);
        Recipe recipe = Recipe.builder()
                .id(recipeId).userId(userId).recipeName("Pasta").publicRecipe(false)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(docSnap.toObject(Recipe.class)).thenReturn(recipe);
        when(querySnapshot.getDocuments()).thenReturn(List.of(docSnap));

        // Mock bookmark batch-get
        CollectionReference savedTopRef = mock(CollectionReference.class);
        DocumentReference savedUserDocRef = mock(DocumentReference.class);
        CollectionReference savedSubRef = mock(CollectionReference.class);
        DocumentReference savedDocRef = mock(DocumentReference.class);
        when(firestore.collection("savedRecipes")).thenReturn(savedTopRef);
        when(savedTopRef.document(userId)).thenReturn(savedUserDocRef);
        when(savedUserDocRef.collection("recipes")).thenReturn(savedSubRef);
        when(savedSubRef.document(recipeId)).thenReturn(savedDocRef);

        // Mock likes batch-get
        CollectionReference likesTopRef = mock(CollectionReference.class);
        DocumentReference likesRecipeDocRef = mock(DocumentReference.class);
        CollectionReference likesSubRef = mock(CollectionReference.class);
        DocumentReference likesUserDocRef = mock(DocumentReference.class);
        when(firestore.collection("likes")).thenReturn(likesTopRef);
        when(likesTopRef.document(recipeId)).thenReturn(likesRecipeDocRef);
        when(likesRecipeDocRef.collection("users")).thenReturn(likesSubRef);
        when(likesSubRef.document(userId)).thenReturn(likesUserDocRef);

        com.google.cloud.firestore.DocumentSnapshot bookmarkSnap =
                mock(com.google.cloud.firestore.DocumentSnapshot.class);
        when(bookmarkSnap.exists()).thenReturn(true);
        com.google.cloud.firestore.DocumentSnapshot likeSnap =
                mock(com.google.cloud.firestore.DocumentSnapshot.class);
        when(likeSnap.exists()).thenReturn(false);
        @SuppressWarnings("unchecked")
        ApiFuture<List<com.google.cloud.firestore.DocumentSnapshot>> getAllFuture1 =
                mock(ApiFuture.class);
        @SuppressWarnings("unchecked")
        ApiFuture<List<com.google.cloud.firestore.DocumentSnapshot>> getAllFuture2 =
                mock(ApiFuture.class);
        when(getAllFuture1.get()).thenReturn(List.of(bookmarkSnap));
        when(getAllFuture2.get()).thenReturn(List.of(likeSnap));
        when(firestore.getAll(any(DocumentReference[].class)))
                .thenReturn(getAllFuture1)
                .thenReturn(getAllFuture2);

        // Act
        List<RecipeResponse> recipes = recipeService.getUserRecipes(userId);

        // Assert
        assertNotNull(recipes);
        assertEquals(1, recipes.size());
        assertTrue(recipes.get(0).isSavedByCurrentUser());
    }

    @Test
    void getUserRecipes_WithFirestore_EmptyRecipes_SkipsBatchCheck()
            throws ExecutionException, InterruptedException {
        // Arrange
        String userId = "user123";

        CollectionReference recipesRef = mock(CollectionReference.class);
        when(firestore.collection("recipes")).thenReturn(recipesRef);
        Query userQuery = mock(Query.class);
        when(recipesRef.whereEqualTo("userId", userId)).thenReturn(userQuery);

        @SuppressWarnings("unchecked")
        ApiFuture<QuerySnapshot> queryFuture = mock(ApiFuture.class);
        QuerySnapshot querySnapshot = mock(QuerySnapshot.class);
        when(userQuery.get()).thenReturn(queryFuture);
        when(queryFuture.get()).thenReturn(querySnapshot);
        when(querySnapshot.getDocuments()).thenReturn(List.of());

        // Act
        List<RecipeResponse> recipes = recipeService.getUserRecipes(userId);

        // Assert
        assertNotNull(recipes);
        assertTrue(recipes.isEmpty());
        verify(firestore, never()).getAll(any(DocumentReference[].class));
    }

    // ── saveRecipeForUser ────────────────────────────────────────────────────

    @Test
    void saveRecipeForUser_Success() throws ExecutionException, InterruptedException {
        // Arrange
        String recipeId = "recipe123";
        String userId = "user123";

        CollectionReference recipesRef = mock(CollectionReference.class);
        CollectionReference savedTopRef = mock(CollectionReference.class);
        DocumentReference savedUserDocRef = mock(DocumentReference.class);
        CollectionReference savedSubRef = mock(CollectionReference.class);
        DocumentReference savedDocRef = mock(DocumentReference.class);

        // recipe existence check - returns a public recipe
        when(firestore.collection("recipes")).thenReturn(recipesRef);
        when(recipesRef.document(recipeId)).thenReturn(documentReference);
        @SuppressWarnings("unchecked")
        ApiFuture<com.google.cloud.firestore.DocumentSnapshot> snapFuture = mock(ApiFuture.class);
        com.google.cloud.firestore.DocumentSnapshot snapDoc =
                mock(com.google.cloud.firestore.DocumentSnapshot.class);
        when(documentReference.get()).thenReturn(snapFuture);
        when(snapFuture.get()).thenReturn(snapDoc);
        when(snapDoc.exists()).thenReturn(true);
        Recipe publicRecipe = Recipe.builder()
                .id(recipeId).userId("other-user").recipeName("Pasta").publicRecipe(true)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(snapDoc.toObject(Recipe.class)).thenReturn(publicRecipe);

        // save document ref chain
        when(firestore.collection("savedRecipes")).thenReturn(savedTopRef);
        when(savedTopRef.document(userId)).thenReturn(savedUserDocRef);
        when(savedUserDocRef.collection("recipes")).thenReturn(savedSubRef);
        when(savedSubRef.document(recipeId)).thenReturn(savedDocRef);

        // mock transaction to succeed
        @SuppressWarnings("unchecked")
        ApiFuture<Object> txFuture = mock(ApiFuture.class);
        when(txFuture.get()).thenReturn(null);
        when(firestore.runTransaction(any())).thenReturn(txFuture);

        // Act & Assert (no exception)
        recipeService.saveRecipeForUser(recipeId, userId);
        verify(firestore).runTransaction(any());
    }

    @Test
    void saveRecipeForUser_PrivateRecipeNotOwned_ThrowsNotFoundException()
            throws ExecutionException, InterruptedException {
        // Arrange
        String recipeId = "privateRecipe";
        String userId = "user123";
        String ownerId = "other-user";

        CollectionReference recipesRef = mock(CollectionReference.class);
        when(firestore.collection("recipes")).thenReturn(recipesRef);
        when(recipesRef.document(recipeId)).thenReturn(documentReference);
        @SuppressWarnings("unchecked")
        ApiFuture<com.google.cloud.firestore.DocumentSnapshot> snapFuture = mock(ApiFuture.class);
        com.google.cloud.firestore.DocumentSnapshot snapDoc =
                mock(com.google.cloud.firestore.DocumentSnapshot.class);
        when(documentReference.get()).thenReturn(snapFuture);
        when(snapFuture.get()).thenReturn(snapDoc);
        when(snapDoc.exists()).thenReturn(true);
        Recipe privateRecipe = Recipe.builder()
                .id(recipeId).userId(ownerId).recipeName("Secret").publicRecipe(false)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(snapDoc.toObject(Recipe.class)).thenReturn(privateRecipe);

        // Act & Assert
        org.springframework.web.server.ResponseStatusException ex = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> recipeService.saveRecipeForUser(recipeId, userId));
        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(firestore, never()).runTransaction(any());
    }

    @Test
    void saveRecipeForUser_RecipeNotFound_ThrowsNotFoundException()
            throws ExecutionException, InterruptedException {
        // Arrange
        String recipeId = "nonexistent";
        String userId = "user123";

        CollectionReference recipesRef = mock(CollectionReference.class);
        when(firestore.collection("recipes")).thenReturn(recipesRef);
        when(recipesRef.document(recipeId)).thenReturn(documentReference);
        @SuppressWarnings("unchecked")
        ApiFuture<com.google.cloud.firestore.DocumentSnapshot> snapFuture = mock(ApiFuture.class);
        com.google.cloud.firestore.DocumentSnapshot snapDoc =
                mock(com.google.cloud.firestore.DocumentSnapshot.class);
        when(documentReference.get()).thenReturn(snapFuture);
        when(snapFuture.get()).thenReturn(snapDoc);
        when(snapDoc.exists()).thenReturn(false);

        // Act & Assert
        org.springframework.web.server.ResponseStatusException ex = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> recipeService.saveRecipeForUser(recipeId, userId));
        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void saveRecipeForUser_FirestoreNotConfigured_ThrowsServiceUnavailable() {
        RecipeService svc = new RecipeService();
        ReflectionTestUtils.setField(svc, "recipesCollection", "recipes");

        org.springframework.web.server.ResponseStatusException ex = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> svc.saveRecipeForUser("recipe123", "user123"));
        assertEquals(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
    }

    // ── unsaveRecipeForUser ──────────────────────────────────────────────────

    @Test
    void unsaveRecipeForUser_Success() throws ExecutionException, InterruptedException {
        // Arrange
        String recipeId = "recipe123";
        String userId = "user123";

        CollectionReference savedTopRef = mock(CollectionReference.class);
        DocumentReference savedUserDocRef = mock(DocumentReference.class);
        CollectionReference savedSubRef = mock(CollectionReference.class);
        DocumentReference savedDocRef = mock(DocumentReference.class);

        when(firestore.collection("savedRecipes")).thenReturn(savedTopRef);
        when(savedTopRef.document(userId)).thenReturn(savedUserDocRef);
        when(savedUserDocRef.collection("recipes")).thenReturn(savedSubRef);
        when(savedSubRef.document(recipeId)).thenReturn(savedDocRef);
        when(savedDocRef.delete()).thenReturn(writeResultFuture);
        when(writeResultFuture.get()).thenReturn(writeResult);

        // Act & Assert (no exception)
        recipeService.unsaveRecipeForUser(recipeId, userId);
        verify(savedDocRef).delete();
    }

    @Test
    void unsaveRecipeForUser_FirestoreNotConfigured_ThrowsServiceUnavailable() {
        RecipeService svc = new RecipeService();
        ReflectionTestUtils.setField(svc, "recipesCollection", "recipes");

        org.springframework.web.server.ResponseStatusException ex = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> svc.unsaveRecipeForUser("recipe123", "user123"));
        assertEquals(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
    }

    // ── getSavedRecipes ──────────────────────────────────────────────────────

    @Test
    void getSavedRecipes_NoFirestore_ReturnsEmptyPagedResponse() {
        RecipeService svc = new RecipeService();
        ReflectionTestUtils.setField(svc, "recipesCollection", "recipes");

        PagedRecipeResponse response = svc.getSavedRecipes("user123", null, 20);

        assertNotNull(response);
        assertTrue(response.getRecipes().isEmpty());
        assertEquals(20, response.getSize());
        assertEquals(0, response.getTotalCount());
        assertNull(response.getNextPageToken());
    }

    @Test
    void getSavedRecipes_SizeExceedsMax_ThrowsBadRequest() {
        RecipeService svc = new RecipeService();
        ReflectionTestUtils.setField(svc, "recipesCollection", "recipes");

        org.springframework.web.server.ResponseStatusException ex = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> svc.getSavedRecipes("user123", null, 101));
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void getSavedRecipes_ZeroSize_ThrowsBadRequest() {
        RecipeService svc = new RecipeService();
        ReflectionTestUtils.setField(svc, "recipesCollection", "recipes");

        org.springframework.web.server.ResponseStatusException ex = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> svc.getSavedRecipes("user123", null, 0));
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void getSavedRecipes_InvalidPageToken_ThrowsBadRequest() {
        RecipeService svc = new RecipeService();
        ReflectionTestUtils.setField(svc, "recipesCollection", "recipes");

        org.springframework.web.server.ResponseStatusException ex = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> svc.getSavedRecipes("user123", "not-valid-base64!!!", 10));
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void getSavedRecipes_WithFirestore_ReturnsSavedRecipes()
            throws ExecutionException, InterruptedException {
        // Arrange saved-recipes subcollection
        String userId = "user123";
        String recipeId = "recipe123";

        CollectionReference savedTopRef = mock(CollectionReference.class);
        DocumentReference savedUserDocRef = mock(DocumentReference.class);
        CollectionReference savedSubRef = mock(CollectionReference.class);

        when(firestore.collection("savedRecipes")).thenReturn(savedTopRef);
        when(savedTopRef.document(userId)).thenReturn(savedUserDocRef);
        when(savedUserDocRef.collection("recipes")).thenReturn(savedSubRef);

        // count
        com.google.cloud.firestore.AggregateQuery aggQuery =
                mock(com.google.cloud.firestore.AggregateQuery.class);
        when(savedSubRef.count()).thenReturn(aggQuery);
        @SuppressWarnings("unchecked")
        ApiFuture<com.google.cloud.firestore.AggregateQuerySnapshot> cntFuture =
                mock(ApiFuture.class);
        com.google.cloud.firestore.AggregateQuerySnapshot cntSnap =
                mock(com.google.cloud.firestore.AggregateQuerySnapshot.class);
        when(aggQuery.get()).thenReturn(cntFuture);
        when(cntFuture.get()).thenReturn(cntSnap);
        when(cntSnap.getCount()).thenReturn(1L);

        // orderBy → limit chain
        Query orderedQuery = mock(Query.class);
        when(savedSubRef.orderBy("savedAt", Query.Direction.DESCENDING)).thenReturn(orderedQuery);
        Query limitedQuery = mock(Query.class);
        when(orderedQuery.limit(20)).thenReturn(limitedQuery);

        // query execution with one saved-doc entry
        @SuppressWarnings("unchecked")
        ApiFuture<QuerySnapshot> qFuture = mock(ApiFuture.class);
        QuerySnapshot qSnap = mock(QuerySnapshot.class);
        when(limitedQuery.get()).thenReturn(qFuture);
        when(qFuture.get()).thenReturn(qSnap);

        com.google.cloud.firestore.QueryDocumentSnapshot savedDocSnap =
                mock(com.google.cloud.firestore.QueryDocumentSnapshot.class);
        when(savedDocSnap.getId()).thenReturn(recipeId);
        com.google.cloud.Timestamp ts =
                com.google.cloud.Timestamp.ofTimeSecondsAndNanos(1743000000L, 0);
        when(savedDocSnap.getTimestamp("savedAt")).thenReturn(ts);
        when(qSnap.getDocuments()).thenReturn(List.of(savedDocSnap));
        when(qSnap.isEmpty()).thenReturn(false);

        // batch recipe fetch via firestore.getAll(...)
        CollectionReference recipesRef = mock(CollectionReference.class);
        when(firestore.collection("recipes")).thenReturn(recipesRef);
        when(recipesRef.document(recipeId)).thenReturn(documentReference);
        com.google.cloud.firestore.DocumentSnapshot rSnap =
                mock(com.google.cloud.firestore.DocumentSnapshot.class);
        when(rSnap.exists()).thenReturn(true);
        Recipe recipe = Recipe.builder()
                .id(recipeId).userId(userId).recipeName("Pasta").publicRecipe(false)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(rSnap.toObject(Recipe.class)).thenReturn(recipe);

        // Mock likes collection for batch like check
        CollectionReference likesTopRef = mock(CollectionReference.class);
        DocumentReference likesRecipeDocRef = mock(DocumentReference.class);
        CollectionReference likesSubRef = mock(CollectionReference.class);
        DocumentReference likesUserDocRef = mock(DocumentReference.class);
        when(firestore.collection("likes")).thenReturn(likesTopRef);
        when(likesTopRef.document(recipeId)).thenReturn(likesRecipeDocRef);
        when(likesRecipeDocRef.collection("users")).thenReturn(likesSubRef);
        when(likesSubRef.document(userId)).thenReturn(likesUserDocRef);

        com.google.cloud.firestore.DocumentSnapshot likeSnap =
                mock(com.google.cloud.firestore.DocumentSnapshot.class);
        when(likeSnap.exists()).thenReturn(false);

        @SuppressWarnings("unchecked")
        ApiFuture<List<com.google.cloud.firestore.DocumentSnapshot>> getAllFuture1 =
                mock(ApiFuture.class);
        @SuppressWarnings("unchecked")
        ApiFuture<List<com.google.cloud.firestore.DocumentSnapshot>> getAllFuture2 =
                mock(ApiFuture.class);
        when(getAllFuture1.get()).thenReturn(List.of(rSnap));
        when(getAllFuture2.get()).thenReturn(List.of(likeSnap));
        when(firestore.getAll(any(DocumentReference[].class)))
                .thenReturn(getAllFuture1)
                .thenReturn(getAllFuture2);

        // Act
        PagedRecipeResponse response = recipeService.getSavedRecipes(userId, null, 20);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getRecipes().size());
        assertEquals(1L, response.getTotalCount());
        assertTrue(response.getRecipes().get(0).isSavedByCurrentUser());
        assertNotNull(response.getNextPageToken());
    }

    // ── isSavedByCurrentUser on getRecipe ────────────────────────────────────

    @Test
    void getRecipe_PopulatesIsSavedByCurrentUser_WhenSaved()
            throws ExecutionException, InterruptedException {
        String recipeId = "recipe123";
        String userId = "user123";

        // main recipe fetch
        CollectionReference recipesRef = mock(CollectionReference.class);
        when(firestore.collection("recipes")).thenReturn(recipesRef);
        when(recipesRef.document(recipeId)).thenReturn(documentReference);
        @SuppressWarnings("unchecked")
        ApiFuture<com.google.cloud.firestore.DocumentSnapshot> rFuture = mock(ApiFuture.class);
        com.google.cloud.firestore.DocumentSnapshot rSnap =
                mock(com.google.cloud.firestore.DocumentSnapshot.class);
        when(documentReference.get()).thenReturn(rFuture);
        when(rFuture.get()).thenReturn(rSnap);
        when(rSnap.exists()).thenReturn(true);
        Recipe recipe = Recipe.builder()
                .id(recipeId).userId(userId).recipeName("Pasta").publicRecipe(false)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(rSnap.toObject(Recipe.class)).thenReturn(recipe);

        // saved-status check
        CollectionReference savedTopRef = mock(CollectionReference.class);
        DocumentReference savedUserDocRef = mock(DocumentReference.class);
        CollectionReference savedSubRef = mock(CollectionReference.class);
        DocumentReference savedRecipeDocRef = mock(DocumentReference.class);

        when(firestore.collection("savedRecipes")).thenReturn(savedTopRef);
        when(savedTopRef.document(userId)).thenReturn(savedUserDocRef);
        when(savedUserDocRef.collection("recipes")).thenReturn(savedSubRef);
        when(savedSubRef.document(recipeId)).thenReturn(savedRecipeDocRef);

        @SuppressWarnings("unchecked")
        ApiFuture<com.google.cloud.firestore.DocumentSnapshot> savedFuture = mock(ApiFuture.class);
        com.google.cloud.firestore.DocumentSnapshot savedSnap =
                mock(com.google.cloud.firestore.DocumentSnapshot.class);
        when(savedRecipeDocRef.get()).thenReturn(savedFuture);
        when(savedFuture.get()).thenReturn(savedSnap);
        when(savedSnap.exists()).thenReturn(true);  // recipe IS saved

        // like-status check
        CollectionReference likesTopRef = mock(CollectionReference.class);
        DocumentReference likesRecipeDocRef = mock(DocumentReference.class);
        CollectionReference likesSubRef = mock(CollectionReference.class);
        DocumentReference likesUserDocRef = mock(DocumentReference.class);
        when(firestore.collection("likes")).thenReturn(likesTopRef);
        when(likesTopRef.document(recipeId)).thenReturn(likesRecipeDocRef);
        when(likesRecipeDocRef.collection("users")).thenReturn(likesSubRef);
        when(likesSubRef.document(userId)).thenReturn(likesUserDocRef);

        @SuppressWarnings("unchecked")
        ApiFuture<com.google.cloud.firestore.DocumentSnapshot> likeFuture = mock(ApiFuture.class);
        com.google.cloud.firestore.DocumentSnapshot likeSnap =
                mock(com.google.cloud.firestore.DocumentSnapshot.class);
        when(likesUserDocRef.get()).thenReturn(likeFuture);
        when(likeFuture.get()).thenReturn(likeSnap);
        when(likeSnap.exists()).thenReturn(false);  // recipe is NOT liked

        // Act
        RecipeResponse response = recipeService.getRecipe(recipeId, userId);

        // Assert
        assertNotNull(response);
        assertTrue(response.isSavedByCurrentUser());
    }

    // ── getFeed tests ─────────────────────────────────────────────────────────

    @Test
    void getFeed_SizeExceedsMax_ThrowsBadRequest() {
        org.springframework.web.server.ResponseStatusException ex = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> recipeService.getFeed("user1", null, 101));
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void getFeed_ZeroSize_ThrowsBadRequest() {
        org.springframework.web.server.ResponseStatusException ex = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> recipeService.getFeed("user1", null, 0));
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void getFeed_InvalidPageToken_ThrowsBadRequest() {
        org.springframework.web.server.ResponseStatusException ex = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> recipeService.getFeed("user1", "not-valid-base64!!!", 10));
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void getFeed_EmptyFollowingList_ReturnsEmptyFeed() {
        when(followService.getFollowingIds("user1")).thenReturn(List.of());

        PagedRecipeResponse response = recipeService.getFeed("user1", null, 20);

        assertNotNull(response);
        assertTrue(response.getRecipes().isEmpty());
        assertEquals(20, response.getSize());
        assertEquals(0, response.getTotalCount());
        assertNull(response.getNextPageToken());
    }

    @Test
    void getFeed_NullFollowService_ReturnsEmptyFeed() {
        RecipeService serviceWithoutFollowService = new RecipeService();
        ReflectionTestUtils.setField(serviceWithoutFollowService, "firestore", firestore);
        ReflectionTestUtils.setField(serviceWithoutFollowService, "recipesCollection", "recipes");
        ReflectionTestUtils.setField(serviceWithoutFollowService, "savedRecipesCollection",
                "savedRecipes");

        PagedRecipeResponse response = serviceWithoutFollowService.getFeed("user1", null, 20);

        assertNotNull(response);
        assertTrue(response.getRecipes().isEmpty());
        assertEquals(20, response.getSize());
        assertNull(response.getNextPageToken());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getFeed_WithFirestore_ReturnsRecipesFromFollowedUsers()
            throws ExecutionException, InterruptedException {
        when(followService.getFollowingIds("user1")).thenReturn(List.of("followed1"));

        CollectionReference collectionRef = mock(CollectionReference.class);
        when(firestore.collection("recipes")).thenReturn(collectionRef);

        Query whereInQuery = mock(Query.class);
        when(collectionRef.whereIn(eq("userId"), any())).thenReturn(whereInQuery);
        Query publicQuery = mock(Query.class);
        when(whereInQuery.whereEqualTo("isPublic", true)).thenReturn(publicQuery);
        Query orderedQuery = mock(Query.class);
        when(publicQuery.orderBy("createdAt", Query.Direction.DESCENDING)).thenReturn(orderedQuery);
        Query limitedQuery = mock(Query.class);
        when(orderedQuery.limit(21)).thenReturn(limitedQuery); // size+1 = 21

        ApiFuture<QuerySnapshot> queryFuture = mock(ApiFuture.class);
        QuerySnapshot querySnapshot = mock(QuerySnapshot.class);
        when(limitedQuery.get()).thenReturn(queryFuture);
        when(queryFuture.get()).thenReturn(querySnapshot);

        QueryDocumentSnapshot docSnapshot = mock(QueryDocumentSnapshot.class);
        Recipe recipe = Recipe.builder()
                .id("r1").userId("followed1").recipeName("Pasta").publicRecipe(true)
                .createdAt(Instant.ofEpochSecond(1743000000L)).build();
        when(docSnapshot.toObject(Recipe.class)).thenReturn(recipe);
        when(querySnapshot.getDocuments()).thenReturn(List.of(docSnapshot));

        // Mock likes collection for batch like check
        CollectionReference likesTopRef = mock(CollectionReference.class);
        DocumentReference likesRecipeDocRef = mock(DocumentReference.class);
        CollectionReference likesSubRef = mock(CollectionReference.class);
        DocumentReference likesUserDocRef = mock(DocumentReference.class);
        when(firestore.collection("likes")).thenReturn(likesTopRef);
        when(likesTopRef.document("r1")).thenReturn(likesRecipeDocRef);
        when(likesRecipeDocRef.collection("users")).thenReturn(likesSubRef);
        when(likesSubRef.document("user1")).thenReturn(likesUserDocRef);

        DocumentSnapshot likeSnap = mock(DocumentSnapshot.class);
        when(likeSnap.exists()).thenReturn(false);
        ApiFuture<List<DocumentSnapshot>> getAllFuture = mock(ApiFuture.class);
        when(getAllFuture.get()).thenReturn(List.of(likeSnap));
        when(firestore.getAll(any(DocumentReference[].class))).thenReturn(getAllFuture);

        PagedRecipeResponse response = recipeService.getFeed("user1", null, 20);

        assertNotNull(response);
        assertEquals(1, response.getRecipes().size());
        assertEquals(20, response.getSize());
        // only 1 result which is < size=20, so no next page token
        assertNull(response.getNextPageToken());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getFeed_HasMoreThanPageSize_SetsNextPageToken()
            throws ExecutionException, InterruptedException {
        when(followService.getFollowingIds("user1")).thenReturn(List.of("followed1"));

        CollectionReference collectionRef = mock(CollectionReference.class);
        when(firestore.collection("recipes")).thenReturn(collectionRef);

        Query whereInQuery = mock(Query.class);
        when(collectionRef.whereIn(eq("userId"), any())).thenReturn(whereInQuery);
        Query publicQuery = mock(Query.class);
        when(whereInQuery.whereEqualTo("isPublic", true)).thenReturn(publicQuery);
        Query orderedQuery = mock(Query.class);
        when(publicQuery.orderBy("createdAt", Query.Direction.DESCENDING)).thenReturn(orderedQuery);
        Query limitedQuery = mock(Query.class);
        when(orderedQuery.limit(3)).thenReturn(limitedQuery); // size+1 = 3

        ApiFuture<QuerySnapshot> queryFuture = mock(ApiFuture.class);
        QuerySnapshot querySnapshot = mock(QuerySnapshot.class);
        when(limitedQuery.get()).thenReturn(queryFuture);
        when(queryFuture.get()).thenReturn(querySnapshot);

        // Return size+1=3 docs to signal there's a next page
        QueryDocumentSnapshot doc1 = mock(QueryDocumentSnapshot.class);
        QueryDocumentSnapshot doc2 = mock(QueryDocumentSnapshot.class);
        QueryDocumentSnapshot doc3 = mock(QueryDocumentSnapshot.class);
        Recipe r1 = Recipe.builder().id("r1").userId("followed1").recipeName("A").publicRecipe(true)
                .createdAt(Instant.ofEpochSecond(1743000002L)).build();
        Recipe r2 = Recipe.builder().id("r2").userId("followed1").recipeName("B").publicRecipe(true)
                .createdAt(Instant.ofEpochSecond(1743000001L)).build();
        Recipe r3 = Recipe.builder().id("r3").userId("followed1").recipeName("C").publicRecipe(true)
                .createdAt(Instant.ofEpochSecond(1743000000L)).build();
        when(doc1.toObject(Recipe.class)).thenReturn(r1);
        when(doc2.toObject(Recipe.class)).thenReturn(r2);
        when(doc3.toObject(Recipe.class)).thenReturn(r3);
        when(querySnapshot.getDocuments()).thenReturn(List.of(doc1, doc2, doc3));

        // Mock likes collection for batch like check (page is r1, r2)
        CollectionReference likesTopRef = mock(CollectionReference.class);
        when(firestore.collection("likes")).thenReturn(likesTopRef);
        DocumentReference likesR1DocRef = mock(DocumentReference.class);
        DocumentReference likesR2DocRef = mock(DocumentReference.class);
        CollectionReference likesR1SubRef = mock(CollectionReference.class);
        CollectionReference likesR2SubRef = mock(CollectionReference.class);
        DocumentReference likesR1UserDocRef = mock(DocumentReference.class);
        DocumentReference likesR2UserDocRef = mock(DocumentReference.class);
        when(likesTopRef.document("r1")).thenReturn(likesR1DocRef);
        when(likesTopRef.document("r2")).thenReturn(likesR2DocRef);
        when(likesR1DocRef.collection("users")).thenReturn(likesR1SubRef);
        when(likesR2DocRef.collection("users")).thenReturn(likesR2SubRef);
        when(likesR1SubRef.document("user1")).thenReturn(likesR1UserDocRef);
        when(likesR2SubRef.document("user1")).thenReturn(likesR2UserDocRef);

        DocumentSnapshot likeSnap1 = mock(DocumentSnapshot.class);
        DocumentSnapshot likeSnap2 = mock(DocumentSnapshot.class);
        when(likeSnap1.exists()).thenReturn(false);
        when(likeSnap2.exists()).thenReturn(false);
        ApiFuture<List<DocumentSnapshot>> getAllFuture = mock(ApiFuture.class);
        when(getAllFuture.get()).thenReturn(List.of(likeSnap1, likeSnap2));
        when(firestore.getAll(any(DocumentReference[].class))).thenReturn(getAllFuture);

        PagedRecipeResponse response = recipeService.getFeed("user1", null, 2);

        assertNotNull(response);
        assertEquals(2, response.getRecipes().size()); // only size=2 items returned
        assertNotNull(response.getNextPageToken()); // 3 results > size=2 → token present
    }

    @Test
    @SuppressWarnings("unchecked")
    void getFeed_WithValidPageToken_StartsAfterCursor()
            throws ExecutionException, InterruptedException {
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("1743000000,0".getBytes(StandardCharsets.UTF_8));

        when(followService.getFollowingIds("user1")).thenReturn(List.of("followed1"));

        CollectionReference collectionRef = mock(CollectionReference.class);
        when(firestore.collection("recipes")).thenReturn(collectionRef);

        Query whereInQuery = mock(Query.class);
        when(collectionRef.whereIn(eq("userId"), any())).thenReturn(whereInQuery);
        Query publicQuery = mock(Query.class);
        when(whereInQuery.whereEqualTo("isPublic", true)).thenReturn(publicQuery);
        Query orderedQuery = mock(Query.class);
        when(publicQuery.orderBy("createdAt", Query.Direction.DESCENDING)).thenReturn(orderedQuery);
        Query afterQuery = mock(Query.class);
        when(orderedQuery.startAfter(any(Timestamp.class))).thenReturn(afterQuery);
        Query limitedQuery = mock(Query.class);
        when(afterQuery.limit(11)).thenReturn(limitedQuery); // size+1 = 11

        ApiFuture<QuerySnapshot> queryFuture = mock(ApiFuture.class);
        QuerySnapshot querySnapshot = mock(QuerySnapshot.class);
        when(limitedQuery.get()).thenReturn(queryFuture);
        when(queryFuture.get()).thenReturn(querySnapshot);
        when(querySnapshot.getDocuments()).thenReturn(List.of());

        PagedRecipeResponse response = recipeService.getFeed("user1", token, 10);

        assertNotNull(response);
        assertTrue(response.getRecipes().isEmpty());
        assertNull(response.getNextPageToken());
        verify(orderedQuery).startAfter(any(Timestamp.class));
    }
}

