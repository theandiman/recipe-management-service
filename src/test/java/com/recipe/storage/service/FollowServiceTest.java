package com.recipe.storage.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.SetOptions;
import com.google.cloud.firestore.Transaction;
import com.recipe.storage.dto.FollowUserResponse;
import com.recipe.storage.dto.PagedFollowResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

    @Mock
    private Firestore firestore;

    private FollowService followService;

    @BeforeEach
    void setUp() {
        followService = new FollowService();
        ReflectionTestUtils.setField(followService, "firestore", firestore);
        ReflectionTestUtils.setField(followService, "followsCollection", "follows");
        ReflectionTestUtils.setField(followService, "usersCollection", "users");
    }

    // -------------------------------------------------------------------------
    // followUser tests
    // -------------------------------------------------------------------------

    @Test
    void followUser_SelfFollow_Throws400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> followService.followUser("user1", "user1"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void followUser_NullFirestore_Throws503() {
        FollowService noFirestoreService = new FollowService();
        ReflectionTestUtils.setField(noFirestoreService, "followsCollection", "follows");
        ReflectionTestUtils.setField(noFirestoreService, "usersCollection", "users");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> noFirestoreService.followUser("user1", "user2"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void followUser_NewFollow_RunsTransactionAndUpdatesCounters() throws Exception {
        String followerId = "follower1";
        String followedId = "followed2";

        // Stub doc refs for follows/{followerId}/following/{followedId}
        CollectionReference followsCol = mock(CollectionReference.class);
        DocumentReference followerDoc = mock(DocumentReference.class);
        CollectionReference followingSubcol = mock(CollectionReference.class);
        DocumentReference followDocRef = mock(DocumentReference.class);

        when(firestore.collection("follows")).thenReturn(followsCol);
        when(followsCol.document(followerId)).thenReturn(followerDoc);
        when(followerDoc.collection("following")).thenReturn(followingSubcol);
        when(followingSubcol.document(followedId)).thenReturn(followDocRef);

        // Stub doc refs for follows/{followedId}/followers/{followerId} (reverse index)
        DocumentReference followedDoc = mock(DocumentReference.class);
        CollectionReference followersSubcol = mock(CollectionReference.class);
        DocumentReference reverseFollowDocRef = mock(DocumentReference.class);
        when(followsCol.document(followedId)).thenReturn(followedDoc);
        when(followedDoc.collection("followers")).thenReturn(followersSubcol);
        when(followersSubcol.document(followerId)).thenReturn(reverseFollowDocRef);

        // Stub user doc refs
        CollectionReference usersCol = mock(CollectionReference.class);
        DocumentReference followerUserRef = mock(DocumentReference.class);
        DocumentReference followedUserRef = mock(DocumentReference.class);
        when(firestore.collection("users")).thenReturn(usersCol);
        when(usersCol.document(followerId)).thenReturn(followerUserRef);
        when(usersCol.document(followedId)).thenReturn(followedUserRef);

        // Capture the transaction callback and execute it with a mock Transaction
        Transaction mockTx = mock(Transaction.class);
        ApiFuture<DocumentSnapshot> snapFuture = mock(ApiFuture.class);
        DocumentSnapshot followSnapshot = mock(DocumentSnapshot.class);
        when(followSnapshot.exists()).thenReturn(false);
        when(mockTx.get(followDocRef)).thenReturn(snapFuture);
        when(snapFuture.get()).thenReturn(followSnapshot);
        when(mockTx.set(any(), any())).thenReturn(mockTx);
        when(mockTx.set(any(), any(), any())).thenReturn(mockTx);

        ApiFuture<Boolean> txResult = mock(ApiFuture.class);
        when(txResult.get()).thenReturn(Boolean.TRUE);
        when(firestore.runTransaction(any(Transaction.Function.class))).thenAnswer(invocation -> {
            Transaction.Function<Boolean> fn = invocation.getArgument(0);
            fn.updateCallback(mockTx);
            return txResult;
        });

        followService.followUser(followerId, followedId);

        verify(txResult).get();
        // Verify forward follow doc was created
        verify(mockTx).set(eq(followDocRef), any());
        // Verify reverse follow doc (followers) was created
        verify(mockTx).set(eq(reverseFollowDocRef), any());

        // Verify followerCount and followingCount were incremented on the correct documents
        ArgumentCaptor<Map> followerMapCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map> followedMapCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockTx).set(eq(followerUserRef), followerMapCaptor.capture(),
                any(SetOptions.class));
        verify(mockTx).set(eq(followedUserRef), followedMapCaptor.capture(),
                any(SetOptions.class));

        Map<?, ?> followerMap = followerMapCaptor.getValue();
        assertTrue(followerMap.containsKey("followingCount"),
                "follower profile must have followingCount updated");
        assertInstanceOf(FieldValue.class, followerMap.get("followingCount"));

        Map<?, ?> followedMap = followedMapCaptor.getValue();
        assertTrue(followedMap.containsKey("followerCount"),
                "followed profile must have followerCount updated");
        assertInstanceOf(FieldValue.class, followedMap.get("followerCount"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void followUser_AlreadyFollowing_IsNoOp() throws Exception {
        String followerId = "follower1";
        String followedId = "followed2";

        CollectionReference followsCol = mock(CollectionReference.class);
        DocumentReference followerDoc = mock(DocumentReference.class);
        CollectionReference followingSubcol = mock(CollectionReference.class);
        DocumentReference followDocRef = mock(DocumentReference.class);

        when(firestore.collection("follows")).thenReturn(followsCol);
        when(followsCol.document(followerId)).thenReturn(followerDoc);
        when(followerDoc.collection("following")).thenReturn(followingSubcol);
        when(followingSubcol.document(followedId)).thenReturn(followDocRef);

        CollectionReference usersCol = mock(CollectionReference.class);
        when(firestore.collection("users")).thenReturn(usersCol);

        // Follow doc already exists
        Transaction mockTx = mock(Transaction.class);
        ApiFuture<DocumentSnapshot> snapFuture = mock(ApiFuture.class);
        DocumentSnapshot followSnapshot = mock(DocumentSnapshot.class);
        when(followSnapshot.exists()).thenReturn(true);
        when(mockTx.get(followDocRef)).thenReturn(snapFuture);
        when(snapFuture.get()).thenReturn(followSnapshot);

        ApiFuture<Boolean> txResult = mock(ApiFuture.class);
        when(txResult.get()).thenReturn(Boolean.FALSE);
        when(firestore.runTransaction(any(Transaction.Function.class))).thenAnswer(invocation -> {
            Transaction.Function<Boolean> fn = invocation.getArgument(0);
            fn.updateCallback(mockTx);
            return txResult;
        });

        followService.followUser(followerId, followedId);

        verify(txResult).get();
        // No writes should occur
        verify(mockTx, never()).set(any(), any());
        verify(mockTx, never()).set(any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void followUser_TransactionInterrupted_Throws503() throws Exception {
        String followerId = "follower1";
        String followedId = "followed2";

        CollectionReference followsCol = mock(CollectionReference.class);
        DocumentReference followerDoc = mock(DocumentReference.class);
        CollectionReference followingSubcol = mock(CollectionReference.class);
        DocumentReference followDocRef = mock(DocumentReference.class);

        when(firestore.collection("follows")).thenReturn(followsCol);
        when(followsCol.document(followerId)).thenReturn(followerDoc);
        when(followerDoc.collection("following")).thenReturn(followingSubcol);
        when(followingSubcol.document(followedId)).thenReturn(followDocRef);

        CollectionReference usersCol = mock(CollectionReference.class);
        when(firestore.collection("users")).thenReturn(usersCol);

        ApiFuture<Boolean> txResult = mock(ApiFuture.class);
        when(firestore.runTransaction(any(Transaction.Function.class))).thenReturn(txResult);
        when(txResult.get()).thenThrow(new InterruptedException("interrupted"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> followService.followUser(followerId, followedId));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void followUser_TransactionExecutionException_Throws503() throws Exception {
        String followerId = "follower1";
        String followedId = "followed2";

        CollectionReference followsCol = mock(CollectionReference.class);
        DocumentReference followerDoc = mock(DocumentReference.class);
        CollectionReference followingSubcol = mock(CollectionReference.class);
        DocumentReference followDocRef = mock(DocumentReference.class);

        when(firestore.collection("follows")).thenReturn(followsCol);
        when(followsCol.document(followerId)).thenReturn(followerDoc);
        when(followerDoc.collection("following")).thenReturn(followingSubcol);
        when(followingSubcol.document(followedId)).thenReturn(followDocRef);

        CollectionReference usersCol = mock(CollectionReference.class);
        when(firestore.collection("users")).thenReturn(usersCol);

        ApiFuture<Boolean> txResult = mock(ApiFuture.class);
        when(firestore.runTransaction(any(Transaction.Function.class))).thenReturn(txResult);
        when(txResult.get()).thenThrow(new ExecutionException("error", new RuntimeException()));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> followService.followUser(followerId, followedId));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
    }

    // -------------------------------------------------------------------------
    // unfollowUser tests
    // -------------------------------------------------------------------------

    @Test
    void unfollowUser_SelfUnfollow_Throws400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> followService.unfollowUser("user1", "user1"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void unfollowUser_NullFirestore_Throws503() {
        FollowService noFirestoreService = new FollowService();
        ReflectionTestUtils.setField(noFirestoreService, "followsCollection", "follows");
        ReflectionTestUtils.setField(noFirestoreService, "usersCollection", "users");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> noFirestoreService.unfollowUser("user1", "user2"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void unfollowUser_ExistingFollow_DeletesAndDecrementsCounters() throws Exception {
        String followerId = "follower1";
        String followedId = "followed2";

        CollectionReference followsCol = mock(CollectionReference.class);
        DocumentReference followerDoc = mock(DocumentReference.class);
        CollectionReference followingSubcol = mock(CollectionReference.class);
        DocumentReference followDocRef = mock(DocumentReference.class);

        when(firestore.collection("follows")).thenReturn(followsCol);
        when(followsCol.document(followerId)).thenReturn(followerDoc);
        when(followerDoc.collection("following")).thenReturn(followingSubcol);
        when(followingSubcol.document(followedId)).thenReturn(followDocRef);

        // Stub reverse index doc refs for follows/{followedId}/followers/{followerId}
        DocumentReference followedDoc = mock(DocumentReference.class);
        CollectionReference followersSubcol = mock(CollectionReference.class);
        DocumentReference reverseFollowDocRef = mock(DocumentReference.class);
        when(followsCol.document(followedId)).thenReturn(followedDoc);
        when(followedDoc.collection("followers")).thenReturn(followersSubcol);
        when(followersSubcol.document(followerId)).thenReturn(reverseFollowDocRef);

        CollectionReference usersCol = mock(CollectionReference.class);
        DocumentReference followerUserRef = mock(DocumentReference.class);
        DocumentReference followedUserRef = mock(DocumentReference.class);
        when(firestore.collection("users")).thenReturn(usersCol);
        when(usersCol.document(followerId)).thenReturn(followerUserRef);
        when(usersCol.document(followedId)).thenReturn(followedUserRef);

        Transaction mockTx = mock(Transaction.class);
        ApiFuture<DocumentSnapshot> snapFuture = mock(ApiFuture.class);
        DocumentSnapshot followSnapshot = mock(DocumentSnapshot.class);
        when(followSnapshot.exists()).thenReturn(true);
        when(mockTx.get(followDocRef)).thenReturn(snapFuture);
        when(snapFuture.get()).thenReturn(followSnapshot);
        when(mockTx.delete(any())).thenReturn(mockTx);
        when(mockTx.set(any(), any(), any())).thenReturn(mockTx);

        ApiFuture<Boolean> txResult = mock(ApiFuture.class);
        when(txResult.get()).thenReturn(Boolean.TRUE);
        when(firestore.runTransaction(any(Transaction.Function.class))).thenAnswer(invocation -> {
            Transaction.Function<Boolean> fn = invocation.getArgument(0);
            fn.updateCallback(mockTx);
            return txResult;
        });

        followService.unfollowUser(followerId, followedId);

        verify(txResult).get();
        // Verify forward follow doc was deleted
        verify(mockTx).delete(followDocRef);
        // Verify reverse follow doc (followers) was deleted
        verify(mockTx).delete(reverseFollowDocRef);

        // Verify followerCount and followingCount were decremented on the correct documents
        ArgumentCaptor<Map> followerMapCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map> followedMapCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockTx).set(eq(followerUserRef), followerMapCaptor.capture(),
                any(SetOptions.class));
        verify(mockTx).set(eq(followedUserRef), followedMapCaptor.capture(),
                any(SetOptions.class));

        Map<?, ?> followerMap = followerMapCaptor.getValue();
        assertTrue(followerMap.containsKey("followingCount"),
                "follower profile must have followingCount decremented");
        assertInstanceOf(FieldValue.class, followerMap.get("followingCount"));

        Map<?, ?> followedMap = followedMapCaptor.getValue();
        assertTrue(followedMap.containsKey("followerCount"),
                "followed profile must have followerCount decremented");
        assertInstanceOf(FieldValue.class, followedMap.get("followerCount"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void unfollowUser_NotFollowing_IsNoOp() throws Exception {
        String followerId = "follower1";
        String followedId = "followed2";

        CollectionReference followsCol = mock(CollectionReference.class);
        DocumentReference followerDoc = mock(DocumentReference.class);
        CollectionReference followingSubcol = mock(CollectionReference.class);
        DocumentReference followDocRef = mock(DocumentReference.class);

        when(firestore.collection("follows")).thenReturn(followsCol);
        when(followsCol.document(followerId)).thenReturn(followerDoc);
        when(followerDoc.collection("following")).thenReturn(followingSubcol);
        when(followingSubcol.document(followedId)).thenReturn(followDocRef);

        CollectionReference usersCol = mock(CollectionReference.class);
        when(firestore.collection("users")).thenReturn(usersCol);

        Transaction mockTx = mock(Transaction.class);
        ApiFuture<DocumentSnapshot> snapFuture = mock(ApiFuture.class);
        DocumentSnapshot followSnapshot = mock(DocumentSnapshot.class);
        when(followSnapshot.exists()).thenReturn(false);
        when(mockTx.get(followDocRef)).thenReturn(snapFuture);
        when(snapFuture.get()).thenReturn(followSnapshot);

        ApiFuture<Boolean> txResult = mock(ApiFuture.class);
        when(txResult.get()).thenReturn(Boolean.FALSE);
        when(firestore.runTransaction(any(Transaction.Function.class))).thenAnswer(invocation -> {
            Transaction.Function<Boolean> fn = invocation.getArgument(0);
            fn.updateCallback(mockTx);
            return txResult;
        });

        followService.unfollowUser(followerId, followedId);

        verify(txResult).get();
        // No writes should occur
        verify(mockTx, never()).delete(any());
        verify(mockTx, never()).set(any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void unfollowUser_TransactionInterrupted_Throws503() throws Exception {
        String followerId = "follower1";
        String followedId = "followed2";

        CollectionReference followsCol = mock(CollectionReference.class);
        DocumentReference followerDoc = mock(DocumentReference.class);
        CollectionReference followingSubcol = mock(CollectionReference.class);
        DocumentReference followDocRef = mock(DocumentReference.class);

        when(firestore.collection("follows")).thenReturn(followsCol);
        when(followsCol.document(followerId)).thenReturn(followerDoc);
        when(followerDoc.collection("following")).thenReturn(followingSubcol);
        when(followingSubcol.document(followedId)).thenReturn(followDocRef);

        CollectionReference usersCol = mock(CollectionReference.class);
        when(firestore.collection("users")).thenReturn(usersCol);

        ApiFuture<Boolean> txResult = mock(ApiFuture.class);
        when(firestore.runTransaction(any(Transaction.Function.class))).thenReturn(txResult);
        when(txResult.get()).thenThrow(new InterruptedException("interrupted"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> followService.unfollowUser(followerId, followedId));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void unfollowUser_TransactionExecutionException_Throws503() throws Exception {
        String followerId = "follower1";
        String followedId = "followed2";

        CollectionReference followsCol = mock(CollectionReference.class);
        DocumentReference followerDoc = mock(DocumentReference.class);
        CollectionReference followingSubcol = mock(CollectionReference.class);
        DocumentReference followDocRef = mock(DocumentReference.class);

        when(firestore.collection("follows")).thenReturn(followsCol);
        when(followsCol.document(followerId)).thenReturn(followerDoc);
        when(followerDoc.collection("following")).thenReturn(followingSubcol);
        when(followingSubcol.document(followedId)).thenReturn(followDocRef);

        CollectionReference usersCol = mock(CollectionReference.class);
        when(firestore.collection("users")).thenReturn(usersCol);

        ApiFuture<Boolean> txResult = mock(ApiFuture.class);
        when(firestore.runTransaction(any(Transaction.Function.class))).thenReturn(txResult);
        when(txResult.get()).thenThrow(new ExecutionException("error", new RuntimeException()));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> followService.unfollowUser(followerId, followedId));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
    }

    // -------------------------------------------------------------------------
    // getFollowers / getFollowing tests
    // -------------------------------------------------------------------------

    @Test
    void getFollowers_NullFirestore_Throws503() {
        FollowService noFirestoreService = new FollowService();
        ReflectionTestUtils.setField(noFirestoreService, "followsCollection", "follows");
        ReflectionTestUtils.setField(noFirestoreService, "usersCollection", "users");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> noFirestoreService.getFollowers("user1", null, 20));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
    }

    @Test
    void getFollowing_NullFirestore_Throws503() {
        FollowService noFirestoreService = new FollowService();
        ReflectionTestUtils.setField(noFirestoreService, "followsCollection", "follows");
        ReflectionTestUtils.setField(noFirestoreService, "usersCollection", "users");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> noFirestoreService.getFollowing("user1", null, 20));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
    }

    @Test
    void getFollowers_InvalidPageToken_Throws400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> followService.getFollowers("user1", "not-valid-base64!!!", 20));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void getFollowing_InvalidPageToken_Throws400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> followService.getFollowing("user1", "not-valid-base64!!!", 20));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getFollowers_EmptyCollection_ReturnsEmptyList() throws Exception {
        CollectionReference followsCol = mock(CollectionReference.class);
        DocumentReference userDoc = mock(DocumentReference.class);
        CollectionReference followersSubcol = mock(CollectionReference.class);
        Query orderedQuery = mock(Query.class);
        Query limitedQuery = mock(Query.class);
        ApiFuture<QuerySnapshot> queryFuture = mock(ApiFuture.class);
        QuerySnapshot emptySnapshot = mock(QuerySnapshot.class);

        when(firestore.collection("follows")).thenReturn(followsCol);
        when(followsCol.document("user1")).thenReturn(userDoc);
        when(userDoc.collection("followers")).thenReturn(followersSubcol);
        when(followersSubcol.orderBy("followedAt", Query.Direction.ASCENDING)).thenReturn(orderedQuery);
        when(orderedQuery.limit(20)).thenReturn(limitedQuery);
        when(limitedQuery.get()).thenReturn(queryFuture);
        when(queryFuture.get()).thenReturn(emptySnapshot);
        when(emptySnapshot.isEmpty()).thenReturn(true);
        when(emptySnapshot.getDocuments()).thenReturn(Collections.emptyList());

        PagedFollowResponse result = followService.getFollowers("user1", null, 20);

        assertTrue(result.getUsers().isEmpty());
        assertNull(result.getNextPageToken());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getFollowing_EmptyCollection_ReturnsEmptyList() throws Exception {
        CollectionReference followsCol = mock(CollectionReference.class);
        DocumentReference userDoc = mock(DocumentReference.class);
        CollectionReference followingSubcol = mock(CollectionReference.class);
        Query orderedQuery = mock(Query.class);
        Query limitedQuery = mock(Query.class);
        ApiFuture<QuerySnapshot> queryFuture = mock(ApiFuture.class);
        QuerySnapshot emptySnapshot = mock(QuerySnapshot.class);

        when(firestore.collection("follows")).thenReturn(followsCol);
        when(followsCol.document("user1")).thenReturn(userDoc);
        when(userDoc.collection("following")).thenReturn(followingSubcol);
        when(followingSubcol.orderBy("followedAt", Query.Direction.ASCENDING)).thenReturn(orderedQuery);
        when(orderedQuery.limit(20)).thenReturn(limitedQuery);
        when(limitedQuery.get()).thenReturn(queryFuture);
        when(queryFuture.get()).thenReturn(emptySnapshot);
        when(emptySnapshot.isEmpty()).thenReturn(true);
        when(emptySnapshot.getDocuments()).thenReturn(Collections.emptyList());

        PagedFollowResponse result = followService.getFollowing("user1", null, 20);

        assertTrue(result.getUsers().isEmpty());
        assertNull(result.getNextPageToken());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getFollowers_WithResults_ReturnsEnrichedListWithNextToken() throws Exception {
        String ownerUid = "owner1";
        String followerUid = "followerA";
        long tsSeconds = 1000000L;
        int tsNanos = 500;

        CollectionReference followsCol = mock(CollectionReference.class);
        DocumentReference userDoc = mock(DocumentReference.class);
        CollectionReference followersSubcol = mock(CollectionReference.class);
        Query orderedQuery = mock(Query.class);
        Query limitedQuery = mock(Query.class);
        ApiFuture<QuerySnapshot> queryFuture = mock(ApiFuture.class);
        QuerySnapshot snapshot = mock(QuerySnapshot.class);
        QueryDocumentSnapshot followerDoc = mock(QueryDocumentSnapshot.class);

        when(firestore.collection("follows")).thenReturn(followsCol);
        when(followsCol.document(ownerUid)).thenReturn(userDoc);
        when(userDoc.collection("followers")).thenReturn(followersSubcol);
        when(followersSubcol.orderBy("followedAt", Query.Direction.ASCENDING)).thenReturn(orderedQuery);
        when(orderedQuery.limit(1)).thenReturn(limitedQuery);
        when(limitedQuery.get()).thenReturn(queryFuture);
        when(queryFuture.get()).thenReturn(snapshot);

        Timestamp ts = Timestamp.ofTimeSecondsAndNanos(tsSeconds, tsNanos);
        when(followerDoc.getId()).thenReturn(followerUid);
        when(followerDoc.getTimestamp("followedAt")).thenReturn(ts);
        when(snapshot.isEmpty()).thenReturn(false);
        when(snapshot.getDocuments()).thenReturn(List.of(followerDoc));

        // Stub batch user profile lookup via getAll
        CollectionReference usersCol = mock(CollectionReference.class);
        DocumentReference followerUserDocRef = mock(DocumentReference.class);
        when(firestore.collection("users")).thenReturn(usersCol);
        when(usersCol.document(followerUid)).thenReturn(followerUserDocRef);

        ApiFuture<List<DocumentSnapshot>> getAllFuture = mock(ApiFuture.class);
        DocumentSnapshot followerUserDoc = mock(DocumentSnapshot.class);
        when(firestore.getAll(any(DocumentReference[].class))).thenReturn(getAllFuture);
        when(getAllFuture.get()).thenReturn(List.of(followerUserDoc));
        when(followerUserDoc.exists()).thenReturn(true);
        when(followerUserDoc.getString("displayName")).thenReturn("Alice");
        when(followerUserDoc.getString("avatarUrl")).thenReturn("https://example.com/alice.jpg");

        PagedFollowResponse result = followService.getFollowers(ownerUid, null, 1);

        assertEquals(1, result.getUsers().size());
        FollowUserResponse user = result.getUsers().get(0);
        assertEquals(followerUid, user.getUid());
        assertEquals("Alice", user.getDisplayName());
        assertEquals("https://example.com/alice.jpg", user.getPhotoUrl());

        // Next page token should be encoded timestamp
        String expectedToken = Base64.getUrlEncoder().withoutPadding()
                .encodeToString((tsSeconds + "," + tsNanos).getBytes(StandardCharsets.UTF_8));
        assertEquals(expectedToken, result.getNextPageToken());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getFollowing_WithResults_ReturnsEnrichedList() throws Exception {
        String ownerUid = "owner1";
        String followedUid = "followedB";

        CollectionReference followsCol = mock(CollectionReference.class);
        DocumentReference userDoc = mock(DocumentReference.class);
        CollectionReference followingSubcol = mock(CollectionReference.class);
        Query orderedQuery = mock(Query.class);
        Query limitedQuery = mock(Query.class);
        ApiFuture<QuerySnapshot> queryFuture = mock(ApiFuture.class);
        QuerySnapshot snapshot = mock(QuerySnapshot.class);
        QueryDocumentSnapshot followedDoc = mock(QueryDocumentSnapshot.class);

        when(firestore.collection("follows")).thenReturn(followsCol);
        when(followsCol.document(ownerUid)).thenReturn(userDoc);
        when(userDoc.collection("following")).thenReturn(followingSubcol);
        when(followingSubcol.orderBy("followedAt", Query.Direction.ASCENDING)).thenReturn(orderedQuery);
        when(orderedQuery.limit(20)).thenReturn(limitedQuery);
        when(limitedQuery.get()).thenReturn(queryFuture);
        when(queryFuture.get()).thenReturn(snapshot);

        when(followedDoc.getId()).thenReturn(followedUid);
        when(followedDoc.getTimestamp("followedAt")).thenReturn(
                Timestamp.ofTimeSecondsAndNanos(2000000L, 0));
        when(snapshot.isEmpty()).thenReturn(false);
        when(snapshot.getDocuments()).thenReturn(List.of(followedDoc));

        CollectionReference usersCol = mock(CollectionReference.class);
        DocumentReference followedUserDocRef = mock(DocumentReference.class);
        when(firestore.collection("users")).thenReturn(usersCol);
        when(usersCol.document(followedUid)).thenReturn(followedUserDocRef);

        ApiFuture<List<DocumentSnapshot>> getAllFuture = mock(ApiFuture.class);
        DocumentSnapshot followedUserDoc = mock(DocumentSnapshot.class);
        when(firestore.getAll(any(DocumentReference[].class))).thenReturn(getAllFuture);
        when(getAllFuture.get()).thenReturn(List.of(followedUserDoc));
        when(followedUserDoc.exists()).thenReturn(true);
        when(followedUserDoc.getString("displayName")).thenReturn("Bob");
        when(followedUserDoc.getString("avatarUrl")).thenReturn(null);

        PagedFollowResponse result = followService.getFollowing(ownerUid, null, 20);

        assertEquals(1, result.getUsers().size());
        FollowUserResponse user = result.getUsers().get(0);
        assertEquals(followedUid, user.getUid());
        assertEquals("Bob", user.getDisplayName());
        assertNull(user.getPhotoUrl());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getFollowers_WithValidPageToken_UsesStartAfterCursor() throws Exception {
        long seconds = 1000000L;
        int nanos = 500;
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString((seconds + "," + nanos).getBytes(StandardCharsets.UTF_8));

        CollectionReference followsCol = mock(CollectionReference.class);
        DocumentReference userDoc = mock(DocumentReference.class);
        CollectionReference followersSubcol = mock(CollectionReference.class);
        Query orderedQuery = mock(Query.class);
        Query startAfterQuery = mock(Query.class);
        Query limitedQuery = mock(Query.class);
        ApiFuture<QuerySnapshot> queryFuture = mock(ApiFuture.class);
        QuerySnapshot emptySnapshot = mock(QuerySnapshot.class);

        when(firestore.collection("follows")).thenReturn(followsCol);
        when(followsCol.document("user1")).thenReturn(userDoc);
        when(userDoc.collection("followers")).thenReturn(followersSubcol);
        when(followersSubcol.orderBy("followedAt", Query.Direction.ASCENDING)).thenReturn(orderedQuery);
        when(orderedQuery.startAfter(any(Timestamp.class))).thenReturn(startAfterQuery);
        when(startAfterQuery.limit(20)).thenReturn(limitedQuery);
        when(limitedQuery.get()).thenReturn(queryFuture);
        when(queryFuture.get()).thenReturn(emptySnapshot);
        when(emptySnapshot.isEmpty()).thenReturn(true);
        when(emptySnapshot.getDocuments()).thenReturn(Collections.emptyList());

        followService.getFollowers("user1", token, 20);

        verify(orderedQuery).startAfter(Timestamp.ofTimeSecondsAndNanos(seconds, nanos));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getFollowers_NullFollowedAtAndMissingProfile_ReturnsNullFieldsAndNoNextToken()
            throws Exception {
        String ownerUid = "owner1";
        String followerUid = "deletedUser";

        CollectionReference followsCol = mock(CollectionReference.class);
        DocumentReference userDoc = mock(DocumentReference.class);
        CollectionReference followersSubcol = mock(CollectionReference.class);
        Query orderedQuery = mock(Query.class);
        Query limitedQuery = mock(Query.class);
        ApiFuture<QuerySnapshot> queryFuture = mock(ApiFuture.class);
        QuerySnapshot snapshot = mock(QuerySnapshot.class);
        QueryDocumentSnapshot followerDoc = mock(QueryDocumentSnapshot.class);

        when(firestore.collection("follows")).thenReturn(followsCol);
        when(followsCol.document(ownerUid)).thenReturn(userDoc);
        when(userDoc.collection("followers")).thenReturn(followersSubcol);
        when(followersSubcol.orderBy("followedAt", Query.Direction.ASCENDING)).thenReturn(orderedQuery);
        when(orderedQuery.limit(20)).thenReturn(limitedQuery);
        when(limitedQuery.get()).thenReturn(queryFuture);
        when(queryFuture.get()).thenReturn(snapshot);

        // followedAt is null (e.g. missing field) -> next page token should be null
        when(followerDoc.getId()).thenReturn(followerUid);
        when(followerDoc.getTimestamp("followedAt")).thenReturn(null);
        when(snapshot.isEmpty()).thenReturn(false);
        when(snapshot.getDocuments()).thenReturn(List.of(followerDoc));

        // User profile doc does not exist -> displayName and photoUrl should be null
        CollectionReference usersCol = mock(CollectionReference.class);
        DocumentReference followerUserDocRef = mock(DocumentReference.class);
        when(firestore.collection("users")).thenReturn(usersCol);
        when(usersCol.document(followerUid)).thenReturn(followerUserDocRef);

        ApiFuture<List<DocumentSnapshot>> getAllFuture = mock(ApiFuture.class);
        DocumentSnapshot missingUserDoc = mock(DocumentSnapshot.class);
        when(firestore.getAll(any(DocumentReference[].class))).thenReturn(getAllFuture);
        when(getAllFuture.get()).thenReturn(List.of(missingUserDoc));
        when(missingUserDoc.exists()).thenReturn(false);

        PagedFollowResponse result = followService.getFollowers(ownerUid, null, 20);

        assertEquals(1, result.getUsers().size());
        FollowUserResponse user = result.getUsers().get(0);
        assertEquals(followerUid, user.getUid());
        assertNull(user.getDisplayName());
        assertNull(user.getPhotoUrl());
        // No followedAt timestamp -> no next page token
        assertNull(result.getNextPageToken());
    }

    // -------------------------------------------------------------------------
    // isFollowing tests
    // -------------------------------------------------------------------------

    @Test
    void isFollowing_NullFirestore_ReturnsFalse() {
        FollowService noFirestoreService = new FollowService();
        ReflectionTestUtils.setField(noFirestoreService, "followsCollection", "follows");
        ReflectionTestUtils.setField(noFirestoreService, "usersCollection", "users");

        assertFalse(noFirestoreService.isFollowing("follower1", "followed1"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void isFollowing_DocumentExists_ReturnsTrue() throws Exception {
        CollectionReference followsCol = mock(CollectionReference.class);
        DocumentReference followerDoc = mock(DocumentReference.class);
        CollectionReference followingSubcol = mock(CollectionReference.class);
        DocumentReference followDocRef = mock(DocumentReference.class);
        ApiFuture<DocumentSnapshot> future = mock(ApiFuture.class);
        DocumentSnapshot snapshot = mock(DocumentSnapshot.class);

        when(firestore.collection("follows")).thenReturn(followsCol);
        when(followsCol.document("follower1")).thenReturn(followerDoc);
        when(followerDoc.collection("following")).thenReturn(followingSubcol);
        when(followingSubcol.document("followed1")).thenReturn(followDocRef);
        when(followDocRef.get()).thenReturn(future);
        when(future.get()).thenReturn(snapshot);
        when(snapshot.exists()).thenReturn(true);

        assertTrue(followService.isFollowing("follower1", "followed1"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void isFollowing_DocumentNotExists_ReturnsFalse() throws Exception {
        CollectionReference followsCol = mock(CollectionReference.class);
        DocumentReference followerDoc = mock(DocumentReference.class);
        CollectionReference followingSubcol = mock(CollectionReference.class);
        DocumentReference followDocRef = mock(DocumentReference.class);
        ApiFuture<DocumentSnapshot> future = mock(ApiFuture.class);
        DocumentSnapshot snapshot = mock(DocumentSnapshot.class);

        when(firestore.collection("follows")).thenReturn(followsCol);
        when(followsCol.document("follower1")).thenReturn(followerDoc);
        when(followerDoc.collection("following")).thenReturn(followingSubcol);
        when(followingSubcol.document("followed1")).thenReturn(followDocRef);
        when(followDocRef.get()).thenReturn(future);
        when(future.get()).thenReturn(snapshot);
        when(snapshot.exists()).thenReturn(false);

        assertFalse(followService.isFollowing("follower1", "followed1"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void isFollowing_InterruptedException_ReturnsFalse() throws Exception {
        CollectionReference followsCol = mock(CollectionReference.class);
        DocumentReference followerDoc = mock(DocumentReference.class);
        CollectionReference followingSubcol = mock(CollectionReference.class);
        DocumentReference followDocRef = mock(DocumentReference.class);
        ApiFuture<DocumentSnapshot> future = mock(ApiFuture.class);

        when(firestore.collection("follows")).thenReturn(followsCol);
        when(followsCol.document("follower1")).thenReturn(followerDoc);
        when(followerDoc.collection("following")).thenReturn(followingSubcol);
        when(followingSubcol.document("followed1")).thenReturn(followDocRef);
        when(followDocRef.get()).thenReturn(future);
        when(future.get()).thenThrow(new InterruptedException("interrupted"));

        assertFalse(followService.isFollowing("follower1", "followed1"));
        assertTrue(Thread.interrupted()); // clear the interrupted flag set by the method
    }

    @Test
    @SuppressWarnings("unchecked")
    void isFollowing_ExecutionException_ReturnsFalse() throws Exception {
        CollectionReference followsCol = mock(CollectionReference.class);
        DocumentReference followerDoc = mock(DocumentReference.class);
        CollectionReference followingSubcol = mock(CollectionReference.class);
        DocumentReference followDocRef = mock(DocumentReference.class);
        ApiFuture<DocumentSnapshot> future = mock(ApiFuture.class);

        when(firestore.collection("follows")).thenReturn(followsCol);
        when(followsCol.document("follower1")).thenReturn(followerDoc);
        when(followerDoc.collection("following")).thenReturn(followingSubcol);
        when(followingSubcol.document("followed1")).thenReturn(followDocRef);
        when(followDocRef.get()).thenReturn(future);
        when(future.get()).thenThrow(new ExecutionException("firestore error", new RuntimeException()));

        assertFalse(followService.isFollowing("follower1", "followed1"));
    }

    // -------------------------------------------------------------------------
    // getFollowingIds tests
    // -------------------------------------------------------------------------

    @Test
    void getFollowingIds_NullFirestore_ReturnsEmptyList() {
        FollowService noFirestoreService = new FollowService();
        ReflectionTestUtils.setField(noFirestoreService, "followsCollection", "follows");
        ReflectionTestUtils.setField(noFirestoreService, "usersCollection", "users");

        List<String> result = noFirestoreService.getFollowingIds("user1");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getFollowingIds_WithFirestore_ReturnsIds() throws Exception {
        CollectionReference followsCol = mock(CollectionReference.class);
        DocumentReference userDoc = mock(DocumentReference.class);
        CollectionReference followingSubcol = mock(CollectionReference.class);
        Query limitedQuery = mock(Query.class);
        ApiFuture<QuerySnapshot> queryFuture = mock(ApiFuture.class);
        QuerySnapshot snapshot = mock(QuerySnapshot.class);
        QueryDocumentSnapshot followDoc1 = mock(QueryDocumentSnapshot.class);
        QueryDocumentSnapshot followDoc2 = mock(QueryDocumentSnapshot.class);

        when(firestore.collection("follows")).thenReturn(followsCol);
        when(followsCol.document("user1")).thenReturn(userDoc);
        when(userDoc.collection("following")).thenReturn(followingSubcol);
        when(followingSubcol.limit(anyInt())).thenReturn(limitedQuery);
        when(limitedQuery.get()).thenReturn(queryFuture);
        when(queryFuture.get()).thenReturn(snapshot);
        when(followDoc1.getId()).thenReturn("followed1");
        when(followDoc2.getId()).thenReturn("followed2");
        when(snapshot.getDocuments()).thenReturn(List.of(followDoc1, followDoc2));

        List<String> result = followService.getFollowingIds("user1");

        assertEquals(2, result.size());
        assertTrue(result.contains("followed1"));
        assertTrue(result.contains("followed2"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getFollowingIds_WithFirestore_EmptyResult_ReturnsEmptyList() throws Exception {
        CollectionReference followsCol = mock(CollectionReference.class);
        DocumentReference userDoc = mock(DocumentReference.class);
        CollectionReference followingSubcol = mock(CollectionReference.class);
        Query limitedQuery = mock(Query.class);
        ApiFuture<QuerySnapshot> queryFuture = mock(ApiFuture.class);
        QuerySnapshot snapshot = mock(QuerySnapshot.class);

        when(firestore.collection("follows")).thenReturn(followsCol);
        when(followsCol.document("user1")).thenReturn(userDoc);
        when(userDoc.collection("following")).thenReturn(followingSubcol);
        when(followingSubcol.limit(anyInt())).thenReturn(limitedQuery);
        when(limitedQuery.get()).thenReturn(queryFuture);
        when(queryFuture.get()).thenReturn(snapshot);
        when(snapshot.getDocuments()).thenReturn(Collections.emptyList());

        List<String> result = followService.getFollowingIds("user1");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getFollowingIds_InterruptedException_ReturnsEmptyList() throws Exception {
        CollectionReference followsCol = mock(CollectionReference.class);
        DocumentReference userDoc = mock(DocumentReference.class);
        CollectionReference followingSubcol = mock(CollectionReference.class);
        Query limitedQuery = mock(Query.class);
        ApiFuture<QuerySnapshot> queryFuture = mock(ApiFuture.class);

        when(firestore.collection("follows")).thenReturn(followsCol);
        when(followsCol.document("user1")).thenReturn(userDoc);
        when(userDoc.collection("following")).thenReturn(followingSubcol);
        when(followingSubcol.limit(anyInt())).thenReturn(limitedQuery);
        when(limitedQuery.get()).thenReturn(queryFuture);
        when(queryFuture.get()).thenThrow(new InterruptedException("interrupted"));

        List<String> result = followService.getFollowingIds("user1");

        assertNotNull(result);
        assertTrue(result.isEmpty());
        assertTrue(Thread.interrupted()); // clear the interrupted flag set by the method
    }

    @Test
    @SuppressWarnings("unchecked")
    void getFollowingIds_ExecutionException_ReturnsEmptyList() throws Exception {
        CollectionReference followsCol = mock(CollectionReference.class);
        DocumentReference userDoc = mock(DocumentReference.class);
        CollectionReference followingSubcol = mock(CollectionReference.class);
        Query limitedQuery = mock(Query.class);
        ApiFuture<QuerySnapshot> queryFuture = mock(ApiFuture.class);

        when(firestore.collection("follows")).thenReturn(followsCol);
        when(followsCol.document("user1")).thenReturn(userDoc);
        when(userDoc.collection("following")).thenReturn(followingSubcol);
        when(followingSubcol.limit(anyInt())).thenReturn(limitedQuery);
        when(limitedQuery.get()).thenReturn(queryFuture);
        when(queryFuture.get()).thenThrow(new ExecutionException("error", new RuntimeException()));

        List<String> result = followService.getFollowingIds("user1");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
