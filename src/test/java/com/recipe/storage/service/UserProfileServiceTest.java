package com.recipe.storage.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import com.recipe.shared.model.Recipe;
import com.recipe.storage.dto.RecipeResponse;
import com.recipe.storage.dto.UserProfileResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
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
}
