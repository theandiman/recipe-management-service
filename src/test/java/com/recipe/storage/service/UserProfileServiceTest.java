package com.recipe.storage.service;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.SetOptions;
import com.google.cloud.firestore.Transaction;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import com.recipe.storage.dto.ProfileVisibility;
import com.recipe.shared.model.Recipe;
import com.recipe.storage.dto.RecipeResponse;
import com.recipe.storage.dto.SelfUserProfileResponse;
import com.recipe.storage.dto.UpdateUserProfileRequest;
import com.recipe.storage.dto.UserProfileResponse;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private Firestore firestore;

    @Mock
    private FollowService followService;

    @Mock
    private FirebaseAuth firebaseAuth;

    @Mock
    private UserRecord userRecord;

    @Mock
    private RecipeService recipeService;

    private UserProfileService userProfileService;

    @BeforeEach
    void setUp() {
        userProfileService = new UserProfileService();
        ReflectionTestUtils.setField(userProfileService, "firestore", firestore);
        ReflectionTestUtils.setField(userProfileService, "followService", followService);
        ReflectionTestUtils.setField(userProfileService, "recipeService", recipeService);
        ReflectionTestUtils.setField(userProfileService, "usersCollection", "users");
        ReflectionTestUtils.setField(userProfileService, "recipesCollection", "recipes");
        ReflectionTestUtils.setField(userProfileService, "followsCollection", "follows");
    }

    /**
     * Sets up Firestore query mocks for the recipes collection, returning a QuerySnapshot with the
     * given list of documents.
     */
    private void mockPublicRecipesQuery(String uid, List<QueryDocumentSnapshot> docs)
            throws ExecutionException, InterruptedException {
        CollectionReference recipesCollection = mock(CollectionReference.class);
        Query uidQuery = mock(Query.class);
        Query publicQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        ApiFuture<QuerySnapshot> queryFuture = mock(ApiFuture.class);
        QuerySnapshot querySnapshot = mock(QuerySnapshot.class);

        when(firestore.collection("recipes")).thenReturn(recipesCollection);
        when(recipesCollection.whereEqualTo("userId", uid)).thenReturn(uidQuery);
        when(uidQuery.whereEqualTo("isPublic", true)).thenReturn(publicQuery);
        when(publicQuery.get()).thenReturn(queryFuture);
        when(queryFuture.get()).thenReturn(querySnapshot);
        when(querySnapshot.getDocuments()).thenReturn(docs);
    }

    @SuppressWarnings("unchecked")
    private Transaction mockProfileTransaction(
            DocumentReference userDocumentReference, DocumentSnapshot userDocument)
            throws ExecutionException, InterruptedException {
        Transaction transaction = mock(Transaction.class);
        ApiFuture<DocumentSnapshot> readFuture = mock(ApiFuture.class);

        when(transaction.get(userDocumentReference)).thenReturn(readFuture);
        when(readFuture.get()).thenReturn(userDocument);
        lenient().when(transaction.set(any(), any(), any())).thenReturn(transaction);
        when(firestore.runTransaction(any(Transaction.Function.class))).thenAnswer(invocation -> {
            Transaction.Function<Object> function = invocation.getArgument(0);
            return ApiFutures.immediateFuture(function.updateCallback(transaction));
        });
        return transaction;
    }

    @Test
    void getUserProfile_WithFirestore_ReturnsProfile() throws ExecutionException, InterruptedException {
        // Arrange
        String uid = "user123";

        CollectionReference usersCollection = mock(CollectionReference.class);
        DocumentReference userDocRef = mock(DocumentReference.class);
        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> userFuture = mock(ApiFuture.class);
        DocumentSnapshot userSnapshot = mock(DocumentSnapshot.class);

        when(firestore.collection("users")).thenReturn(usersCollection);
        when(usersCollection.document(uid)).thenReturn(userDocRef);
        when(userDocRef.get()).thenReturn(userFuture);
        when(userFuture.get()).thenReturn(userSnapshot);
        when(userSnapshot.exists()).thenReturn(true);
        when(userSnapshot.getString("displayName")).thenReturn("Andy");
        when(userSnapshot.getString("bio")).thenReturn("I love pasta.");
        when(userSnapshot.getString("avatarUrl")).thenReturn("https://example.com/avatar.jpg");
        when(userSnapshot.getLong("followerCount")).thenReturn(42L);
        when(userSnapshot.getLong("followingCount")).thenReturn(17L);

        // Mock 12 public recipe docs
        List<QueryDocumentSnapshot> recipeDocs = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) {
            QueryDocumentSnapshot doc = mock(QueryDocumentSnapshot.class);
            Recipe recipe = Recipe.builder().id("recipe-" + i).userId(uid).recipeName("Recipe " + i).publicRecipe(true).build();
            when(doc.toObject(Recipe.class)).thenReturn(recipe);
            RecipeResponse recipeResponse = RecipeResponse.builder().id("recipe-" + i).userId(uid).title("Recipe " + i).isPublic(true).build();
            when(recipeService.mapToResponse(recipe)).thenReturn(recipeResponse);
            recipeDocs.add(doc);
        }
        mockPublicRecipesQuery(uid, recipeDocs);

        // Act - unauthenticated caller
        UserProfileResponse response = userProfileService.getUserProfile(uid, null);

        // Assert
        assertNotNull(response);
        assertEquals(uid, response.getUid());
        assertEquals("Andy", response.getDisplayName());
        assertEquals("I love pasta.", response.getBio());
        assertEquals("https://example.com/avatar.jpg", response.getAvatarUrl());
        assertEquals(12L, response.getPublicRecipeCount());
        assertNotNull(response.getPublicRecipes());
        assertEquals(12, response.getPublicRecipes().size());
        assertEquals(42L, response.getFollowerCount());
        assertEquals(17L, response.getFollowingCount());
        assertFalse(response.isFollowedByCurrentUser());
    }

    @Test
    void getUserProfile_AuthenticatedCallerFollowsUser_ReturnsIsFollowedTrue()
            throws ExecutionException, InterruptedException {
        // Arrange
        String uid = "user123";
        String currentUserId = "caller456";

        CollectionReference usersCollection = mock(CollectionReference.class);
        DocumentReference userDocRef = mock(DocumentReference.class);
        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> userFuture = mock(ApiFuture.class);
        DocumentSnapshot userSnapshot = mock(DocumentSnapshot.class);

        when(firestore.collection("users")).thenReturn(usersCollection);
        when(usersCollection.document(uid)).thenReturn(userDocRef);
        when(userDocRef.get()).thenReturn(userFuture);
        when(userFuture.get()).thenReturn(userSnapshot);
        when(userSnapshot.exists()).thenReturn(true);
        when(userSnapshot.getString("displayName")).thenReturn("Andy");
        when(userSnapshot.getString("bio")).thenReturn(null);
        when(userSnapshot.getString("avatarUrl")).thenReturn(null);
        when(userSnapshot.getLong("followerCount")).thenReturn(5L);
        when(userSnapshot.getLong("followingCount")).thenReturn(3L);

        mockPublicRecipesQuery(uid, Collections.emptyList());
        when(followService.isFollowing(currentUserId, uid)).thenReturn(true);

        // Act
        UserProfileResponse response = userProfileService.getUserProfile(uid, currentUserId);

        // Assert
        assertNotNull(response);
        assertTrue(response.isFollowedByCurrentUser());
        assertEquals(5L, response.getFollowerCount());
        assertEquals(3L, response.getFollowingCount());
    }

    @Test
    void getUserProfile_AuthenticatedCallerDoesNotFollowUser_ReturnsIsFollowedFalse()
            throws ExecutionException, InterruptedException {
        // Arrange
        String uid = "user123";
        String currentUserId = "caller456";

        CollectionReference usersCollection = mock(CollectionReference.class);
        DocumentReference userDocRef = mock(DocumentReference.class);
        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> userFuture = mock(ApiFuture.class);
        DocumentSnapshot userSnapshot = mock(DocumentSnapshot.class);

        when(firestore.collection("users")).thenReturn(usersCollection);
        when(usersCollection.document(uid)).thenReturn(userDocRef);
        when(userDocRef.get()).thenReturn(userFuture);
        when(userFuture.get()).thenReturn(userSnapshot);
        when(userSnapshot.exists()).thenReturn(true);
        when(userSnapshot.getString("displayName")).thenReturn("Andy");
        when(userSnapshot.getString("bio")).thenReturn(null);
        when(userSnapshot.getString("avatarUrl")).thenReturn(null);
        when(userSnapshot.getLong("followerCount")).thenReturn(null);
        when(userSnapshot.getLong("followingCount")).thenReturn(null);

        mockPublicRecipesQuery(uid, Collections.emptyList());
        when(followService.isFollowing(currentUserId, uid)).thenReturn(false);

        // Act
        UserProfileResponse response = userProfileService.getUserProfile(uid, currentUserId);

        // Assert
        assertNotNull(response);
        assertFalse(response.isFollowedByCurrentUser());
        // Null counts from Firestore default to 0
        assertEquals(0L, response.getFollowerCount());
        assertEquals(0L, response.getFollowingCount());
    }

    @Test
    void getUserProfile_UnauthenticatedCaller_ReturnsIsFollowedFalse()
            throws ExecutionException, InterruptedException {
        // Arrange
        String uid = "user123";

        CollectionReference usersCollection = mock(CollectionReference.class);
        DocumentReference userDocRef = mock(DocumentReference.class);
        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> userFuture = mock(ApiFuture.class);
        DocumentSnapshot userSnapshot = mock(DocumentSnapshot.class);

        when(firestore.collection("users")).thenReturn(usersCollection);
        when(usersCollection.document(uid)).thenReturn(userDocRef);
        when(userDocRef.get()).thenReturn(userFuture);
        when(userFuture.get()).thenReturn(userSnapshot);
        when(userSnapshot.exists()).thenReturn(true);
        when(userSnapshot.getString(anyString())).thenReturn(null);
        when(userSnapshot.getLong("followerCount")).thenReturn(10L);
        when(userSnapshot.getLong("followingCount")).thenReturn(2L);

        mockPublicRecipesQuery(uid, Collections.emptyList());

        // Act - null currentUserId simulates unauthenticated caller
        UserProfileResponse response = userProfileService.getUserProfile(uid, null);

        // Assert
        assertNotNull(response);
        assertFalse(response.isFollowedByCurrentUser());
        assertEquals(10L, response.getFollowerCount());
        assertEquals(2L, response.getFollowingCount());
    }

    @Test
    void getUserProfile_UserNotFound_Throws404() throws ExecutionException, InterruptedException {
        // Arrange
        String uid = "unknown-user";

        CollectionReference usersCollection = mock(CollectionReference.class);
        DocumentReference userDocRef = mock(DocumentReference.class);
        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> userFuture = mock(ApiFuture.class);
        DocumentSnapshot userSnapshot = mock(DocumentSnapshot.class);

        when(firestore.collection("users")).thenReturn(usersCollection);
        when(usersCollection.document(uid)).thenReturn(userDocRef);
        when(userDocRef.get()).thenReturn(userFuture);
        when(userFuture.get()).thenReturn(userSnapshot);
        when(userSnapshot.exists()).thenReturn(false);
        mockPublicRecipesQuery(uid, Collections.emptyList());

        // Act & Assert
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> userProfileService.getUserProfile(uid, null));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void getUserProfile_UserMissingInFirestoreButPresentInFirebaseAuth_ReturnsFallbackProfile()
            throws Exception {
        // Arrange
        String uid = "author-123";
        ReflectionTestUtils.setField(userProfileService, "firebaseAuth", firebaseAuth);

        CollectionReference usersCollection = mock(CollectionReference.class);
        DocumentReference userDocRef = mock(DocumentReference.class);
        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> userFuture = mock(ApiFuture.class);
        DocumentSnapshot userSnapshot = mock(DocumentSnapshot.class);

        when(firestore.collection("users")).thenReturn(usersCollection);
        when(usersCollection.document(uid)).thenReturn(userDocRef);
        when(userDocRef.get()).thenReturn(userFuture);
        when(userFuture.get()).thenReturn(userSnapshot);
        when(userSnapshot.exists()).thenReturn(false);

        // 4 public recipes
        List<QueryDocumentSnapshot> recipeDocs = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            QueryDocumentSnapshot doc = mock(QueryDocumentSnapshot.class);
            Recipe recipe = Recipe.builder().id("recipe-" + i).userId(uid).recipeName("Recipe " + i).publicRecipe(true).build();
            when(doc.toObject(Recipe.class)).thenReturn(recipe);
            RecipeResponse recipeResponse = RecipeResponse.builder().id("recipe-" + i).userId(uid).title("Recipe " + i).isPublic(true).build();
            when(recipeService.mapToResponse(recipe)).thenReturn(recipeResponse);
            recipeDocs.add(doc);
        }
        mockPublicRecipesQuery(uid, recipeDocs);

        when(firebaseAuth.getUser(uid)).thenReturn(userRecord);
        when(userRecord.getDisplayName()).thenReturn("Chef Andy");
        when(userRecord.getPhotoUrl()).thenReturn("https://example.com/chef-andy.png");

        // Act
        UserProfileResponse response = userProfileService.getUserProfile(uid, null);

        // Assert
        assertNotNull(response);
        assertEquals(uid, response.getUid());
        assertEquals("Chef Andy", response.getDisplayName());
        assertEquals("https://example.com/chef-andy.png", response.getAvatarUrl());
        assertEquals(4L, response.getPublicRecipeCount());
        assertEquals(0L, response.getFollowerCount());
        assertEquals(0L, response.getFollowingCount());
        assertFalse(response.isFollowedByCurrentUser());
    }

    @Test
    void getUserProfile_NullFirestore_Throws503() {
        // Arrange
        UserProfileService serviceWithoutFirestore = new UserProfileService();
        ReflectionTestUtils.setField(serviceWithoutFirestore, "usersCollection", "users");
        ReflectionTestUtils.setField(serviceWithoutFirestore, "recipesCollection", "recipes");

        // Act & Assert
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> serviceWithoutFirestore.getUserProfile("uid123", null));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
    }

    @Test
    void getUserProfile_NullDocument_Throws404() throws ExecutionException, InterruptedException {
        // Arrange
        String uid = "user123";

        CollectionReference usersCollection = mock(CollectionReference.class);
        DocumentReference userDocRef = mock(DocumentReference.class);
        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> userFuture = mock(ApiFuture.class);

        when(firestore.collection("users")).thenReturn(usersCollection);
        when(usersCollection.document(uid)).thenReturn(userDocRef);
        when(userDocRef.get()).thenReturn(userFuture);
        when(userFuture.get()).thenReturn(null);
        mockPublicRecipesQuery(uid, Collections.emptyList());

        // Act & Assert
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> userProfileService.getUserProfile(uid, null));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void getUserProfile_UserDocumentInterrupted_Throws503() throws ExecutionException, InterruptedException {
        // Arrange
        String uid = "user123";

        CollectionReference usersCollection = mock(CollectionReference.class);
        DocumentReference userDocRef = mock(DocumentReference.class);
        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> userFuture = mock(ApiFuture.class);

        when(firestore.collection("users")).thenReturn(usersCollection);
        when(usersCollection.document(uid)).thenReturn(userDocRef);
        when(userDocRef.get()).thenReturn(userFuture);
        when(userFuture.get()).thenThrow(new InterruptedException("interrupted"));

        // Act & Assert
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> userProfileService.getUserProfile(uid, null));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
    }

    @Test
    void getUserProfile_UserDocumentExecutionException_Throws503() throws ExecutionException, InterruptedException {
        // Arrange
        String uid = "user123";

        CollectionReference usersCollection = mock(CollectionReference.class);
        DocumentReference userDocRef = mock(DocumentReference.class);
        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> userFuture = mock(ApiFuture.class);

        when(firestore.collection("users")).thenReturn(usersCollection);
        when(usersCollection.document(uid)).thenReturn(userDocRef);
        when(userDocRef.get()).thenReturn(userFuture);
        when(userFuture.get()).thenThrow(new ExecutionException("error", new RuntimeException()));

        // Act & Assert
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> userProfileService.getUserProfile(uid, null));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
    }

    @Test
    void getUserProfile_FetchPublicRecipesInterrupted_Throws503() throws ExecutionException, InterruptedException {
        // Arrange
        String uid = "user123";

        CollectionReference usersCollection = mock(CollectionReference.class);
        DocumentReference userDocRef = mock(DocumentReference.class);
        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> userFuture = mock(ApiFuture.class);
        DocumentSnapshot userSnapshot = mock(DocumentSnapshot.class);

        when(firestore.collection("users")).thenReturn(usersCollection);
        when(usersCollection.document(uid)).thenReturn(userDocRef);
        when(userDocRef.get()).thenReturn(userFuture);
        when(userFuture.get()).thenReturn(userSnapshot);
        when(userSnapshot.exists()).thenReturn(true);
        when(userSnapshot.getString("displayName")).thenReturn("Andy");
        when(userSnapshot.getString("bio")).thenReturn(null);
        when(userSnapshot.getString("avatarUrl")).thenReturn(null);

        CollectionReference recipesCollection = mock(CollectionReference.class);
        Query uidQuery = mock(Query.class);
        Query publicQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        ApiFuture<QuerySnapshot> queryFuture = mock(ApiFuture.class);

        when(firestore.collection("recipes")).thenReturn(recipesCollection);
        when(recipesCollection.whereEqualTo("userId", uid)).thenReturn(uidQuery);
        when(uidQuery.whereEqualTo("isPublic", true)).thenReturn(publicQuery);
        when(publicQuery.get()).thenReturn(queryFuture);
        when(queryFuture.get()).thenThrow(new InterruptedException("interrupted"));

        // Act & Assert
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> userProfileService.getUserProfile(uid, null));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
    }

    @Test
    void getUserProfile_FetchPublicRecipesExecutionException_Throws503() throws ExecutionException, InterruptedException {
        // Arrange
        String uid = "user123";

        CollectionReference usersCollection = mock(CollectionReference.class);
        DocumentReference userDocRef = mock(DocumentReference.class);
        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> userFuture = mock(ApiFuture.class);
        DocumentSnapshot userSnapshot = mock(DocumentSnapshot.class);

        when(firestore.collection("users")).thenReturn(usersCollection);
        when(usersCollection.document(uid)).thenReturn(userDocRef);
        when(userDocRef.get()).thenReturn(userFuture);
        when(userFuture.get()).thenReturn(userSnapshot);
        when(userSnapshot.exists()).thenReturn(true);
        when(userSnapshot.getString("displayName")).thenReturn("Andy");
        when(userSnapshot.getString("bio")).thenReturn(null);
        when(userSnapshot.getString("avatarUrl")).thenReturn(null);

        CollectionReference recipesCollection = mock(CollectionReference.class);
        Query uidQuery = mock(Query.class);
        Query publicQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        ApiFuture<QuerySnapshot> queryFuture = mock(ApiFuture.class);

        when(firestore.collection("recipes")).thenReturn(recipesCollection);
        when(recipesCollection.whereEqualTo("userId", uid)).thenReturn(uidQuery);
        when(uidQuery.whereEqualTo("isPublic", true)).thenReturn(publicQuery);
        when(publicQuery.get()).thenReturn(queryFuture);
        when(queryFuture.get()).thenThrow(new ExecutionException("error", new RuntimeException()));

        // Act & Assert
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> userProfileService.getUserProfile(uid, null));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
    }

    @Test
    void getUserProfile_ZeroPublicRecipes_ReturnsEmptyListAndZeroCount()
            throws ExecutionException, InterruptedException {
        // Arrange
        String uid = "user456";

        CollectionReference usersCollection = mock(CollectionReference.class);
        DocumentReference userDocRef = mock(DocumentReference.class);
        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> userFuture = mock(ApiFuture.class);
        DocumentSnapshot userSnapshot = mock(DocumentSnapshot.class);

        when(firestore.collection("users")).thenReturn(usersCollection);
        when(usersCollection.document(uid)).thenReturn(userDocRef);
        when(userDocRef.get()).thenReturn(userFuture);
        when(userFuture.get()).thenReturn(userSnapshot);
        when(userSnapshot.exists()).thenReturn(true);
        when(userSnapshot.getString(anyString())).thenReturn(null);

        mockPublicRecipesQuery(uid, Collections.emptyList());

        // Act
        UserProfileResponse response = userProfileService.getUserProfile(uid, null);

        // Assert
        assertNotNull(response);
        assertEquals(uid, response.getUid());
        assertEquals(0L, response.getPublicRecipeCount());
        assertNotNull(response.getPublicRecipes());
        assertTrue(response.getPublicRecipes().isEmpty());
    }

    @Test
    void getUserProfile_PrivateProfile_DoesNotExposePrivateFields()
            throws ExecutionException, InterruptedException {
        String uid = "private-user";

        CollectionReference usersCollection = mock(CollectionReference.class);
        DocumentReference userDocRef = mock(DocumentReference.class);
        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> userFuture = mock(ApiFuture.class);
        DocumentSnapshot userSnapshot = mock(DocumentSnapshot.class);

        when(firestore.collection("users")).thenReturn(usersCollection);
        when(usersCollection.document(uid)).thenReturn(userDocRef);
        when(userDocRef.get()).thenReturn(userFuture);
        when(userFuture.get()).thenReturn(userSnapshot);
        when(userSnapshot.exists()).thenReturn(true);
        when(userSnapshot.getString("displayName")).thenReturn("Private Chef");
        when(userSnapshot.getString("bio")).thenReturn("This is private.");
        when(userSnapshot.getString("avatarUrl")).thenReturn("https://example.com/private.png");
        when(userSnapshot.getString("visibility")).thenReturn("PRIVATE");
        when(userSnapshot.getLong("followerCount")).thenReturn(24L);
        when(userSnapshot.getLong("followingCount")).thenReturn(12L);
        mockPublicRecipesQuery(uid, Collections.emptyList());

        UserProfileResponse response = userProfileService.getUserProfile(uid, "caller");

        assertEquals(uid, response.getUid());
        assertNull(response.getDisplayName());
        assertNull(response.getBio());
        assertNull(response.getAvatarUrl());
        assertEquals(0L, response.getFollowerCount());
        assertEquals(0L, response.getFollowingCount());
        assertFalse(response.isFollowedByCurrentUser());
    }

    @Test
    void updateSelfProfile_WritesOnlyEditableFieldsAndPreservesDerivedCounts()
            throws ExecutionException, InterruptedException {
        String uid = "profile-owner";
        CollectionReference usersCollection = mock(CollectionReference.class);
        DocumentReference userDocRef = mock(DocumentReference.class);
        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> readFuture = mock(ApiFuture.class);
        @SuppressWarnings("unchecked")
        ApiFuture<com.google.cloud.firestore.WriteResult> writeFuture = mock(ApiFuture.class);
        DocumentSnapshot userSnapshot = mock(DocumentSnapshot.class);
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");

        when(firestore.collection("users")).thenReturn(usersCollection);
        when(usersCollection.document(uid)).thenReturn(userDocRef);
        when(userDocRef.get()).thenReturn(readFuture);
        when(readFuture.get()).thenReturn(userSnapshot);
        when(userSnapshot.exists()).thenReturn(true);
        when(userSnapshot.getString("displayName")).thenReturn("Before");
        when(userSnapshot.getString("bio")).thenReturn("Before bio");
        when(userSnapshot.getString("avatarUrl")).thenReturn("https://example.com/before.png");
        when(userSnapshot.getString("visibility")).thenReturn("PUBLIC");
        when(userSnapshot.getTimestamp("createdAt")).thenReturn(
                Timestamp.ofTimeSecondsAndNanos(createdAt.getEpochSecond(), createdAt.getNano()));
        when(userSnapshot.getTimestamp("updatedAt")).thenReturn(
                Timestamp.ofTimeSecondsAndNanos(createdAt.getEpochSecond(), createdAt.getNano()));
        when(userSnapshot.getLong("followerCount")).thenReturn(9L);
        when(userSnapshot.getLong("followingCount")).thenReturn(4L);
        when(userDocRef.set(any(), eq(SetOptions.merge()))).thenReturn(writeFuture);

        SelfUserProfileResponse response = userProfileService.updateSelfProfile(uid,
                UpdateUserProfileRequest.builder()
                        .displayName("After")
                        .bio("Updated bio")
                        .avatarUrl("https://example.com/after.png")
                        .visibility(ProfileVisibility.PRIVATE)
                        .build());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Map<String, Object>> fieldsCaptor =
                ArgumentCaptor.forClass(java.util.Map.class);
        verify(userDocRef).set(fieldsCaptor.capture(), eq(SetOptions.merge()));
        java.util.Map<String, Object> fields = fieldsCaptor.getValue();

        assertEquals("After", fields.get("displayName"));
        assertEquals("Updated bio", fields.get("bio"));
        assertEquals("https://example.com/after.png", fields.get("avatarUrl"));
        assertEquals("PRIVATE", fields.get("visibility"));
        assertFalse(fields.containsKey("followerCount"));
        assertFalse(fields.containsKey("followingCount"));
        assertEquals(9L, response.getFollowerCount());
        assertEquals(4L, response.getFollowingCount());
        assertEquals(ProfileVisibility.PRIVATE, response.getVisibility());
    }

    @Test
    void getSelfProfile_MissingDocumentBootstrapsFromFirebaseAuth() throws Exception {
        String uid = "new-profile-owner";
        ReflectionTestUtils.setField(userProfileService, "firebaseAuth", firebaseAuth);
        CollectionReference usersCollection = mock(CollectionReference.class);
        DocumentReference userDocRef = mock(DocumentReference.class);
        DocumentSnapshot userSnapshot = mock(DocumentSnapshot.class);

        when(firestore.collection("users")).thenReturn(usersCollection);
        when(usersCollection.document(uid)).thenReturn(userDocRef);
        when(userSnapshot.exists()).thenReturn(false);
        when(firebaseAuth.getUser(uid)).thenReturn(userRecord);
        when(userRecord.getDisplayName()).thenReturn("Bootstrap Chef");
        when(userRecord.getPhotoUrl()).thenReturn("https://example.com/bootstrap.png");
        Transaction transaction = mockProfileTransaction(userDocRef, userSnapshot);

        SelfUserProfileResponse response = userProfileService.getSelfProfile(uid);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Map<String, Object>> fieldsCaptor =
                ArgumentCaptor.forClass(java.util.Map.class);
        verify(transaction).set(eq(userDocRef), fieldsCaptor.capture(), eq(SetOptions.merge()));
        java.util.Map<String, Object> fields = fieldsCaptor.getValue();

        assertEquals("Bootstrap Chef", response.getDisplayName());
        assertEquals("https://example.com/bootstrap.png", response.getAvatarUrl());
        assertEquals(ProfileVisibility.PUBLIC, response.getVisibility());
        assertEquals(0L, response.getFollowerCount());
        assertEquals(0L, response.getFollowingCount());
        assertTrue(fields.containsKey("createdAt"));
        assertTrue(fields.containsKey("updatedAt"));
        assertEquals(0L, fields.get("followerCount"));
        assertEquals(0L, fields.get("followingCount"));
    }

    @Test
    void getSelfProfile_MissingDocumentWithoutFirebaseMetadataBootstrapsEmptyProfile()
            throws Exception {
        String uid = "authenticated-without-auth-metadata";
        CollectionReference usersCollection = mock(CollectionReference.class);
        DocumentReference userDocRef = mock(DocumentReference.class);
        DocumentSnapshot userSnapshot = mock(DocumentSnapshot.class);

        when(firestore.collection("users")).thenReturn(usersCollection);
        when(usersCollection.document(uid)).thenReturn(userDocRef);
        when(userSnapshot.exists()).thenReturn(false);
        Transaction transaction = mockProfileTransaction(userDocRef, userSnapshot);

        SelfUserProfileResponse response = userProfileService.getSelfProfile(uid);

        assertEquals(uid, response.getUid());
        assertNull(response.getDisplayName());
        assertNull(response.getAvatarUrl());
        assertEquals(ProfileVisibility.PUBLIC, response.getVisibility());
        assertEquals(0L, response.getFollowerCount());
        assertEquals(0L, response.getFollowingCount());
        verify(transaction).set(eq(userDocRef), any(), eq(SetOptions.merge()));
    }

    @Test
    void getUserProfile_NegativePersistedCountsAreClampedToZero()
            throws ExecutionException, InterruptedException {
        String uid = "legacy-negative-counts";
        CollectionReference usersCollection = mock(CollectionReference.class);
        DocumentReference userDocRef = mock(DocumentReference.class);
        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> userFuture = mock(ApiFuture.class);
        DocumentSnapshot userSnapshot = mock(DocumentSnapshot.class);

        when(firestore.collection("users")).thenReturn(usersCollection);
        when(usersCollection.document(uid)).thenReturn(userDocRef);
        when(userDocRef.get()).thenReturn(userFuture);
        when(userFuture.get()).thenReturn(userSnapshot);
        when(userSnapshot.exists()).thenReturn(true);
        when(userSnapshot.getString(anyString())).thenReturn(null);
        when(userSnapshot.getLong("followerCount")).thenReturn(-4L);
        when(userSnapshot.getLong("followingCount")).thenReturn(-2L);
        mockPublicRecipesQuery(uid, Collections.emptyList());

        UserProfileResponse response = userProfileService.getUserProfile(uid, null);

        assertEquals(0L, response.getFollowerCount());
        assertEquals(0L, response.getFollowingCount());
    }

    @Test
    void getUserProfile_MissingDocumentWithPublicRecipesReturnsPlaceholderProfile()
            throws ExecutionException, InterruptedException {
        String uid = "legacy-author";
        CollectionReference usersCollection = mock(CollectionReference.class);
        DocumentReference userDocRef = mock(DocumentReference.class);
        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> userFuture = mock(ApiFuture.class);
        DocumentSnapshot userSnapshot = mock(DocumentSnapshot.class);
        QueryDocumentSnapshot recipeDocument = mock(QueryDocumentSnapshot.class);
        Recipe recipe = Recipe.builder()
                .id("published-recipe")
                .userId(uid)
                .recipeName("Published Recipe")
                .publicRecipe(true)
                .build();
        RecipeResponse recipeResponse = RecipeResponse.builder()
                .id("published-recipe")
                .userId(uid)
                .title("Published Recipe")
                .isPublic(true)
                .build();

        when(firestore.collection("users")).thenReturn(usersCollection);
        when(usersCollection.document(uid)).thenReturn(userDocRef);
        when(userDocRef.get()).thenReturn(userFuture);
        when(userFuture.get()).thenReturn(userSnapshot);
        when(userSnapshot.exists()).thenReturn(false);
        when(recipeDocument.toObject(Recipe.class)).thenReturn(recipe);
        when(recipeService.mapToResponse(recipe)).thenReturn(recipeResponse);
        mockPublicRecipesQuery(uid, List.of(recipeDocument));

        UserProfileResponse response = userProfileService.getUserProfile(uid, null);

        assertEquals(uid, response.getUid());
        assertNull(response.getDisplayName());
        assertEquals(1L, response.getPublicRecipeCount());
        assertEquals(0L, response.getFollowerCount());
        assertEquals(0L, response.getFollowingCount());
    }

    @Test
    void getSelfProfile_LegacyPartialProfileBackfillsMetadataWithoutChangingCounts()
            throws Exception {
        String uid = "legacy-partial-profile";
        ReflectionTestUtils.setField(userProfileService, "firebaseAuth", firebaseAuth);
        CollectionReference usersCollection = mock(CollectionReference.class);
        DocumentReference userDocRef = mock(DocumentReference.class);
        DocumentSnapshot userSnapshot = mock(DocumentSnapshot.class);
        Timestamp createdAt = Timestamp.ofTimeSecondsAndNanos(1_700_000_000L, 0);
        Timestamp updatedAt = Timestamp.ofTimeSecondsAndNanos(1_700_000_100L, 0);

        when(firestore.collection("users")).thenReturn(usersCollection);
        when(usersCollection.document(uid)).thenReturn(userDocRef);
        when(userSnapshot.exists()).thenReturn(true);
        when(userSnapshot.getString("displayName")).thenReturn(null);
        when(userSnapshot.getString("bio")).thenReturn(null);
        when(userSnapshot.getString("avatarUrl")).thenReturn(null);
        when(userSnapshot.getString("visibility")).thenReturn(null);
        when(userSnapshot.getTimestamp("createdAt")).thenReturn(createdAt);
        when(userSnapshot.getTimestamp("updatedAt")).thenReturn(updatedAt);
        when(userSnapshot.getLong("followerCount")).thenReturn(8L);
        when(userSnapshot.getLong("followingCount")).thenReturn(6L);
        when(firebaseAuth.getUser(uid)).thenReturn(userRecord);
        when(userRecord.getDisplayName()).thenReturn("Backfilled Chef");
        when(userRecord.getPhotoUrl()).thenReturn("https://example.com/backfilled.png");
        Transaction transaction = mockProfileTransaction(userDocRef, userSnapshot);

        SelfUserProfileResponse response = userProfileService.getSelfProfile(uid);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Map<String, Object>> fieldsCaptor =
                ArgumentCaptor.forClass(java.util.Map.class);
        verify(transaction).set(eq(userDocRef), fieldsCaptor.capture(), eq(SetOptions.merge()));
        java.util.Map<String, Object> fields = fieldsCaptor.getValue();
        assertEquals("Backfilled Chef", fields.get("displayName"));
        assertEquals("https://example.com/backfilled.png", fields.get("avatarUrl"));
        assertEquals("PUBLIC", fields.get("visibility"));
        assertFalse(fields.containsKey("followerCount"));
        assertFalse(fields.containsKey("followingCount"));
        assertEquals(8L, response.getFollowerCount());
        assertEquals(6L, response.getFollowingCount());
    }

    @Test
    void getSelfProfile_CompleteProfileDoesNotRewriteItOnRetry() throws Exception {
        String uid = "complete-profile";
        CollectionReference usersCollection = mock(CollectionReference.class);
        DocumentReference userDocRef = mock(DocumentReference.class);
        DocumentSnapshot userSnapshot = mock(DocumentSnapshot.class);
        Timestamp createdAt = Timestamp.ofTimeSecondsAndNanos(1_700_000_000L, 0);
        Timestamp updatedAt = Timestamp.ofTimeSecondsAndNanos(1_700_000_100L, 0);

        when(firestore.collection("users")).thenReturn(usersCollection);
        when(usersCollection.document(uid)).thenReturn(userDocRef);
        when(userSnapshot.exists()).thenReturn(true);
        when(userSnapshot.getString("displayName")).thenReturn("Existing Chef");
        when(userSnapshot.getString("bio")).thenReturn("Existing bio");
        when(userSnapshot.getString("avatarUrl")).thenReturn("https://example.com/existing.png");
        when(userSnapshot.getString("visibility")).thenReturn("PUBLIC");
        when(userSnapshot.getTimestamp("createdAt")).thenReturn(createdAt);
        when(userSnapshot.getTimestamp("updatedAt")).thenReturn(updatedAt);
        when(userSnapshot.getLong("followerCount")).thenReturn(4L);
        when(userSnapshot.getLong("followingCount")).thenReturn(2L);
        Transaction transaction = mockProfileTransaction(userDocRef, userSnapshot);

        SelfUserProfileResponse response = userProfileService.getSelfProfile(uid);

        verify(transaction, never()).set(any(), any(), any());
        assertEquals("Existing Chef", response.getDisplayName());
        assertEquals(4L, response.getFollowerCount());
        assertEquals(2L, response.getFollowingCount());
    }

    @Test
    @SuppressWarnings("unchecked")
    void repairSelfProfile_ReconcilesOnlyAuthenticatedUsersFollowIndexes() throws Exception {
        String uid = "profile-owner";
        CollectionReference usersCollection = mock(CollectionReference.class);
        CollectionReference followsCollection = mock(CollectionReference.class);
        DocumentReference userDocRef = mock(DocumentReference.class);
        DocumentReference followsDocRef = mock(DocumentReference.class);
        CollectionReference followingCollection = mock(CollectionReference.class);
        CollectionReference followersCollection = mock(CollectionReference.class);
        DocumentSnapshot userSnapshot = mock(DocumentSnapshot.class);
        QuerySnapshot followingSnapshot = mock(QuerySnapshot.class);
        QuerySnapshot followersSnapshot = mock(QuerySnapshot.class);
        Transaction bootstrapTransaction = mock(Transaction.class);
        Transaction reconciliationTransaction = mock(Transaction.class);
        AtomicInteger transactionCalls = new AtomicInteger();

        when(firestore.collection("users")).thenReturn(usersCollection);
        when(firestore.collection("follows")).thenReturn(followsCollection);
        when(usersCollection.document(uid)).thenReturn(userDocRef);
        when(followsCollection.document(uid)).thenReturn(followsDocRef);
        when(followsDocRef.collection("following")).thenReturn(followingCollection);
        when(followsDocRef.collection("followers")).thenReturn(followersCollection);
        when(userSnapshot.exists()).thenReturn(true);
        when(userSnapshot.getString(anyString())).thenReturn(null);
        when(userSnapshot.getLong("followerCount")).thenReturn(99L);
        when(userSnapshot.getLong("followingCount")).thenReturn(77L);
        when(followingSnapshot.getDocuments()).thenReturn(List.of(
                mock(QueryDocumentSnapshot.class), mock(QueryDocumentSnapshot.class)));
        when(followersSnapshot.getDocuments()).thenReturn(List.of(mock(QueryDocumentSnapshot.class)));

        ApiFuture<DocumentSnapshot> bootstrapProfileFuture = mock(ApiFuture.class);
        when(bootstrapTransaction.get(userDocRef)).thenReturn(bootstrapProfileFuture);
        when(bootstrapProfileFuture.get()).thenReturn(userSnapshot);

        ApiFuture<DocumentSnapshot> reconciliationProfileFuture = mock(ApiFuture.class);
        ApiFuture<QuerySnapshot> followingFuture = mock(ApiFuture.class);
        ApiFuture<QuerySnapshot> followersFuture = mock(ApiFuture.class);
        when(reconciliationTransaction.get(userDocRef)).thenReturn(reconciliationProfileFuture);
        when(reconciliationProfileFuture.get()).thenReturn(userSnapshot);
        when(reconciliationTransaction.get(followingCollection)).thenReturn(followingFuture);
        when(followingFuture.get()).thenReturn(followingSnapshot);
        when(reconciliationTransaction.get(followersCollection)).thenReturn(followersFuture);
        when(followersFuture.get()).thenReturn(followersSnapshot);
        when(reconciliationTransaction.set(any(), any(), any())).thenReturn(reconciliationTransaction);

        when(firestore.runTransaction(any(Transaction.Function.class))).thenAnswer(invocation -> {
            Transaction.Function<Object> function = invocation.getArgument(0);
            Transaction transaction = transactionCalls.getAndIncrement() == 0
                    ? bootstrapTransaction
                    : reconciliationTransaction;
            return ApiFutures.immediateFuture(function.updateCallback(transaction));
        });

        SelfUserProfileResponse response = userProfileService.repairSelfProfile(uid);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Map<String, Object>> fieldsCaptor =
                ArgumentCaptor.forClass(java.util.Map.class);
        verify(reconciliationTransaction).set(
                eq(userDocRef), fieldsCaptor.capture(), eq(SetOptions.merge()));
        verify(reconciliationTransaction, never()).delete(any());
        assertEquals(1L, fieldsCaptor.getValue().get("followerCount"));
        assertEquals(2L, fieldsCaptor.getValue().get("followingCount"));
        assertEquals(1L, response.getFollowerCount());
        assertEquals(2L, response.getFollowingCount());
    }
}
