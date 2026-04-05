package com.recipe.storage.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service for managing follow/unfollow operations between users.
 *
 * <p>Follow documents are stored at {@code follows/{followerId}/following/{followedId}}
 * and a reverse index at {@code follows/{followedId}/followers/{followerId}}.
 * Follower and following counts on user profile documents are updated atomically via
 * Firestore transactions to ensure idempotency.
 */
@Slf4j
@Service
public class FollowService {

  private static final String FOLLOWING_SUBCOLLECTION = "following";
  private static final String FOLLOWERS_SUBCOLLECTION = "followers";

  @Autowired(required = false)
  private Firestore firestore;

  @Value("${firestore.collection.follows}")
  private String followsCollection;

  @Value("${firestore.collection.users}")
  private String usersCollection;

  /**
   * Follow a user.
   *
   * <p>Creates a follow document and atomically increments {@code followingCount} on the
   * follower's profile and {@code followerCount} on the followed user's profile.
   * If the follow relationship already exists the operation is a no-op (idempotent).
   *
   * @param followerId the Firebase UID of the user performing the follow
   * @param followedId the Firebase UID of the user being followed
   * @throws ResponseStatusException 400 if followerId equals followedId,
   *     503 if Firestore is not configured or unavailable
   */
  public void followUser(String followerId, String followedId) {
    if (followerId.equals(followedId)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot follow yourself");
    }

    if (firestore == null) {
      log.warn("Firestore not configured - cannot follow user");
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
          "Follow service unavailable");
    }

    DocumentReference followDocRef = firestore.collection(followsCollection)
        .document(followerId)
        .collection(FOLLOWING_SUBCOLLECTION)
        .document(followedId);

    DocumentReference followerUserRef = firestore.collection(usersCollection).document(followerId);
    DocumentReference followedUserRef = firestore.collection(usersCollection).document(followedId);

    try {
      ApiFuture<Boolean> txFuture = firestore.runTransaction(transaction -> {
        DocumentSnapshot followDoc = transaction.get(followDocRef).get();
        if (!followDoc.exists()) {
          DocumentReference reverseFollowDocRef = firestore.collection(followsCollection)
              .document(followedId)
              .collection(FOLLOWERS_SUBCOLLECTION)
              .document(followerId);
          transaction.set(followDocRef,
              Map.of("followedAt", FieldValue.serverTimestamp()));
          transaction.set(reverseFollowDocRef,
              Map.of("followedAt", FieldValue.serverTimestamp()));
          transaction.set(followerUserRef,
              Map.of("followingCount", FieldValue.increment(1)), SetOptions.merge());
          transaction.set(followedUserRef,
              Map.of("followerCount", FieldValue.increment(1)), SetOptions.merge());
          return true;
        }
        return false;
      });
      if (Boolean.TRUE.equals(txFuture.get())) {
        log.info("User {} followed user {}", followerId, followedId);
      } else {
        log.info("User {} already follows user {} - no-op", followerId, followedId);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Interrupted while following user {} -> {}", followerId, followedId, e);
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
          "Follow service unavailable", e);
    } catch (ExecutionException e) {
      log.error("Error while following user {} -> {}", followerId, followedId, e);
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
          "Follow service unavailable", e);
    }
  }

  /**
   * Unfollow a user.
   *
   * <p>Deletes the follow document and atomically decrements {@code followingCount} on the
   * follower's profile and {@code followerCount} on the followed user's profile.
   * If the follow relationship does not exist the operation is a no-op (idempotent).
   *
   * @param followerId the Firebase UID of the user performing the unfollow
   * @param followedId the Firebase UID of the user being unfollowed
   * @throws ResponseStatusException 400 if followerId equals followedId,
   *     503 if Firestore is not configured or unavailable
   */
  public void unfollowUser(String followerId, String followedId) {
    if (followerId.equals(followedId)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot unfollow yourself");
    }

    if (firestore == null) {
      log.warn("Firestore not configured - cannot unfollow user");
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
          "Follow service unavailable");
    }

    DocumentReference followDocRef = firestore.collection(followsCollection)
        .document(followerId)
        .collection(FOLLOWING_SUBCOLLECTION)
        .document(followedId);

    DocumentReference followerUserRef = firestore.collection(usersCollection).document(followerId);
    DocumentReference followedUserRef = firestore.collection(usersCollection).document(followedId);

    try {
      ApiFuture<Boolean> txFuture = firestore.runTransaction(transaction -> {
        DocumentSnapshot followDoc = transaction.get(followDocRef).get();
        if (followDoc.exists()) {
          DocumentReference reverseFollowDocRef = firestore.collection(followsCollection)
              .document(followedId)
              .collection(FOLLOWERS_SUBCOLLECTION)
              .document(followerId);
          transaction.delete(followDocRef);
          transaction.delete(reverseFollowDocRef);
          transaction.set(followerUserRef,
              Map.of("followingCount", FieldValue.increment(-1)), SetOptions.merge());
          transaction.set(followedUserRef,
              Map.of("followerCount", FieldValue.increment(-1)), SetOptions.merge());
          return true;
        }
        return false;
      });
      if (Boolean.TRUE.equals(txFuture.get())) {
        log.info("User {} unfollowed user {}", followerId, followedId);
      } else {
        log.info("User {} does not follow user {} - no-op", followerId, followedId);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Interrupted while unfollowing user {} -> {}", followerId, followedId, e);
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
          "Follow service unavailable", e);
    } catch (ExecutionException e) {
      log.error("Error while unfollowing user {} -> {}", followerId, followedId, e);
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
          "Follow service unavailable", e);
    }
  }

  /**
   * Check whether one user follows another.
   *
   * <p>Returns {@code false} rather than throwing when Firestore is unavailable, so callers
   * can use this for optional profile enrichment without hard-failing the request.
   *
   * @param followerId the Firebase UID of the potential follower
   * @param followedId the Firebase UID of the user potentially being followed
   * @return {@code true} if the follow relationship exists, {@code false} otherwise
   */
  public boolean isFollowing(String followerId, String followedId) {
    if (firestore == null) {
      return false;
    }
    try {
      DocumentReference followDocRef = firestore.collection(followsCollection)
          .document(followerId)
          .collection(FOLLOWING_SUBCOLLECTION)
          .document(followedId);
      DocumentSnapshot followDoc = followDocRef.get().get();
      return followDoc.exists();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Interrupted while checking follow status {} -> {}", followerId, followedId, e);
      return false;
    } catch (ExecutionException e) {
      log.warn("Error checking follow status {} -> {}", followerId, followedId, e);
      return false;
    }
  }

  /**
   * Get all UIDs that the given user is following, up to 500 entries.
   *
   * <p>Returns an empty list rather than throwing when Firestore is unavailable, so callers
   * can degrade gracefully (e.g. returning an empty feed).
   *
   * @param uid the Firebase UID of the user whose following list to retrieve
   * @return list of followed user UIDs (may be empty)
   */
  public List<String> getFollowingIds(String uid) {
    if (firestore == null) {
      return Collections.emptyList();
    }
    try {
      QuerySnapshot snapshot = firestore.collection(followsCollection)
          .document(uid)
          .collection(FOLLOWING_SUBCOLLECTION)
          .limit(500)
          .get()
          .get();
      return snapshot.getDocuments().stream()
          .map(DocumentSnapshot::getId)
          .collect(Collectors.toList());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Interrupted while fetching following IDs for user {}", uid, e);
      return Collections.emptyList();
    } catch (ExecutionException e) {
      log.warn("Error fetching following IDs for user {}", uid, e);
      return Collections.emptyList();
    }
  }

  /**
   * Get a paginated list of users that the given user is following.
   *
   * @param uid       the Firebase UID of the user whose following list to retrieve
   * @param pageToken opaque cursor token from a previous response (null for first page)
   * @param pageSize  maximum number of users to return (1–100)
   * @return paginated list of followed users
   * @throws ResponseStatusException 400 if pageToken or pageSize is invalid,
   *     503 if Firestore is not configured or unavailable
   */
  public PagedFollowResponse getFollowing(String uid, String pageToken, int pageSize) {
    return listFollowSubcollection(uid, FOLLOWING_SUBCOLLECTION, pageToken, pageSize);
  }

  /**
   * Get a paginated list of users following the given user.
   *
   * @param uid       the Firebase UID of the user whose followers to retrieve
   * @param pageToken opaque cursor token from a previous response (null for first page)
   * @param pageSize  maximum number of users to return (1–100)
   * @return paginated list of followers
   * @throws ResponseStatusException 400 if pageToken or pageSize is invalid,
   *     503 if Firestore is not configured or unavailable
   */
  public PagedFollowResponse getFollowers(String uid, String pageToken, int pageSize) {
    return listFollowSubcollection(uid, FOLLOWERS_SUBCOLLECTION, pageToken, pageSize);
  }

  private PagedFollowResponse listFollowSubcollection(
      String uid, String subcollection, String pageToken, int pageSize) {

    if (pageSize < 1) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pageSize must be at least 1");
    }
    if (pageSize > 100) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pageSize must not exceed 100");
    }

    if (firestore == null) {
      log.warn("Firestore not configured - cannot list {}", subcollection);
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
          "Follow service unavailable");
    }

    Timestamp cursor = null;
    if (pageToken != null && !pageToken.isEmpty()) {
      cursor = decodeFollowPageToken(pageToken);
    }

    try {
      Query query = firestore.collection(followsCollection)
          .document(uid)
          .collection(subcollection)
          .orderBy("followedAt", Query.Direction.ASCENDING);

      if (cursor != null) {
        query = query.startAfter(cursor);
      }
      query = query.limit(pageSize);

      QuerySnapshot snapshot = query.get().get();

      List<QueryDocumentSnapshot> followDocs = snapshot.getDocuments();
      List<FollowUserResponse> users = new ArrayList<>();
      if (!followDocs.isEmpty()) {
        DocumentReference[] userRefs = followDocs.stream()
            .map(doc -> firestore.collection(usersCollection).document(doc.getId()))
            .toArray(DocumentReference[]::new);
        List<DocumentSnapshot> userDocs = firestore.getAll(userRefs).get();
        for (int i = 0; i < followDocs.size(); i++) {
          String userId = followDocs.get(i).getId();
          DocumentSnapshot userDoc = userDocs.get(i);
          String displayName = userDoc.exists() ? userDoc.getString("displayName") : null;
          String photoUrl = userDoc.exists() ? userDoc.getString("avatarUrl") : null;
          users.add(FollowUserResponse.builder()
              .uid(userId)
              .displayName(displayName)
              .photoUrl(photoUrl)
              .build());
        }
      }

      String nextToken = encodeFollowPageToken(snapshot);
      log.info("Listed {} {} for user {}", users.size(), subcollection, uid);
      return PagedFollowResponse.builder()
          .users(users)
          .nextPageToken(nextToken)
          .build();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Interrupted while listing {} for user {}", subcollection, uid, e);
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
          "Follow service unavailable", e);
    } catch (ExecutionException e) {
      log.error("Error listing {} for user {}", subcollection, uid, e);
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
          "Follow service unavailable", e);
    }
  }

  private Timestamp decodeFollowPageToken(String pageToken) {
    try {
      byte[] decoded = Base64.getUrlDecoder().decode(pageToken);
      String cursorStr = new String(decoded, StandardCharsets.UTF_8);
      String[] parts = cursorStr.split(",", 2);
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

  private String encodeFollowPageToken(QuerySnapshot snapshot) {
    if (snapshot.isEmpty()) {
      return null;
    }
    List<QueryDocumentSnapshot> docs = snapshot.getDocuments();
    QueryDocumentSnapshot lastDoc = docs.get(docs.size() - 1);
    Timestamp lastTimestamp = lastDoc.getTimestamp("followedAt");
    if (lastTimestamp == null) {
      return null;
    }
    String cursorStr = lastTimestamp.getSeconds() + "," + lastTimestamp.getNanos();
    return Base64.getUrlEncoder().withoutPadding()
        .encodeToString(cursorStr.getBytes(StandardCharsets.UTF_8));
  }
}
