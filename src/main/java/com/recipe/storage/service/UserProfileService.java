package com.recipe.storage.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.SetOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.recipe.shared.model.Recipe;
import com.recipe.storage.dto.ProfileVisibility;
import com.recipe.storage.dto.RecipeResponse;
import com.recipe.storage.dto.SelfUserProfileResponse;
import com.recipe.storage.dto.UpdateUserProfileRequest;
import com.recipe.storage.dto.UserProfile;
import com.recipe.storage.dto.UserProfileResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service for fetching public user profile data.
 */
@Slf4j
@Service
public class UserProfileService {

  @Autowired(required = false)
  private Firestore firestore;

  @Autowired(required = false)
  private FollowService followService;

  @Autowired(required = false)
  private FirebaseAuth firebaseAuth;

  @Autowired(required = false)
  private RecipeService recipeService;

  @Value("${firestore.collection.users}")
  private String usersCollection;

  @Value("${firestore.collection.recipes}")
  private String recipesCollection;

  /**
   * Fetch the public profile for a given user uid.
   *
   * @param uid           The Firebase user ID of the profile to fetch
   * @param currentUserId The Firebase user ID of the authenticated caller, or {@code null} for
   *                      unauthenticated requests; used to populate {@code isFollowedByCurrentUser}
   * @return The public profile response
   * @throws ResponseStatusException 404 if the user does not exist,
   *     503 if Firestore is not configured or unavailable
   */
  public UserProfileResponse getUserProfile(String uid, String currentUserId) {
    requireFirestore();

    try {
      DocumentSnapshot userDocument = getUserDocument(uid);

      boolean hasFirestoreProfile = userDocument != null && userDocument.exists();

      String displayName = null;
      String bio = null;
      String avatarUrl = null;
      Long rawFollowerCount = null;
      Long rawFollowingCount = null;
      ProfileVisibility visibility = ProfileVisibility.PUBLIC;

      if (hasFirestoreProfile) {
        displayName = userDocument.getString("displayName");
        bio = userDocument.getString("bio");
        avatarUrl = userDocument.getString("avatarUrl");
        rawFollowerCount = userDocument.getLong("followerCount");
        rawFollowingCount = userDocument.getLong("followingCount");
        visibility = ProfileVisibility.fromFirestoreValue(userDocument.getString("visibility"));
      }

      UserRecord authUser = null;
      boolean canExposeProfileFields = visibility == ProfileVisibility.PUBLIC;
      boolean needsAuthFallback = canExposeProfileFields && (!hasFirestoreProfile
          || displayName == null || displayName.isBlank()
          || avatarUrl == null || avatarUrl.isBlank());

      if (needsAuthFallback) {
        authUser = getAuthUser(uid);
      }

      if (!hasFirestoreProfile) {
        if (authUser == null) {
          log.warn("User profile not found in Firestore or Firebase Auth: {}", uid);
          throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        log.info("Firestore profile missing for user {}, falling back to Firebase Auth", uid);
      }

      if ((displayName == null || displayName.isBlank()) && authUser != null) {
        displayName = authUser.getDisplayName();
      }
      if ((avatarUrl == null || avatarUrl.isBlank()) && authUser != null) {
        avatarUrl = authUser.getPhotoUrl();
      }

      long followerCount = rawFollowerCount != null ? rawFollowerCount : 0L;
      long followingCount = rawFollowingCount != null ? rawFollowingCount : 0L;

      List<RecipeResponse> publicRecipes = fetchPublicRecipes(uid);
      long publicRecipeCount = publicRecipes.size();

      boolean isFollowedByCurrentUser = canExposeProfileFields && currentUserId != null
          && followService != null
          && followService.isFollowing(currentUserId, uid);

      if (!canExposeProfileFields) {
        displayName = null;
        bio = null;
        avatarUrl = null;
        followerCount = 0L;
        followingCount = 0L;
      }

      log.info("Retrieved public profile for user {} (publicRecipeCount={}, followerCount={}, "
              + "followingCount={}, isFollowedByCurrentUser={})",
          uid, publicRecipeCount, followerCount, followingCount, isFollowedByCurrentUser);

      return UserProfileResponse.builder()
          .uid(uid)
          .displayName(displayName)
          .bio(bio)
          .avatarUrl(avatarUrl)
          .publicRecipeCount(publicRecipeCount)
          .publicRecipes(publicRecipes)
          .followerCount(followerCount)
          .followingCount(followingCount)
          .isFollowedByCurrentUser(isFollowedByCurrentUser)
          .build();
    } catch (ResponseStatusException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Interrupted while fetching user profile from Firestore", e);
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "User profile service unavailable", e);
    } catch (ExecutionException e) {
      log.error("Error fetching user profile from Firestore", e);
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "User profile service unavailable", e);
    }
  }

  /**
   * Returns the complete canonical profile for its authenticated owner.
   *
   * <p>When no profile document exists, Firebase Auth supplies bootstrap display-name and avatar
   * values. The bootstrap result is then persisted as the canonical Firestore profile.
   *
   * @param uid authenticated Firebase UID
   * @return complete owner-only profile
   */
  public SelfUserProfileResponse getSelfProfile(String uid) {
    requireFirestore();

    try {
      DocumentReference userDocumentReference = firestore.collection(usersCollection).document(uid);
      DocumentSnapshot userDocument = userDocumentReference.get().get();
      UserProfile profile;

      if (userDocument == null || !userDocument.exists()) {
        UserRecord authUser = getAuthUser(uid);
        if (authUser == null) {
          throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        Instant now = Instant.now();
        profile = UserProfile.builder()
            .uid(uid)
            .displayName(normalizeOptional(authUser.getDisplayName()))
            .bio(null)
            .avatarUrl(normalizeOptional(authUser.getPhotoUrl()))
            .visibility(ProfileVisibility.PUBLIC)
            .createdAt(now)
            .updatedAt(now)
            .followerCount(0L)
            .followingCount(0L)
            .build();
        writeProfile(userDocumentReference, profile, true);
        log.info("Bootstrapped canonical profile for authenticated user {}", uid);
      } else {
        profile = toUserProfile(uid, userDocument);
      }

      return toSelfResponse(profile);
    } catch (ResponseStatusException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw profileServiceUnavailable(e);
    } catch (ExecutionException e) {
      throw profileServiceUnavailable(e);
    }
  }

  /**
   * Replaces editable fields on the authenticated user's canonical profile.
   *
   * @param uid authenticated Firebase UID
   * @param request validated, editable profile values
   * @return the updated complete owner-only profile
   */
  public SelfUserProfileResponse updateSelfProfile(String uid, UpdateUserProfileRequest request) {
    requireFirestore();

    try {
      DocumentReference userDocumentReference = firestore.collection(usersCollection).document(uid);
      DocumentSnapshot userDocument = userDocumentReference.get().get();
      boolean profileExists = userDocument != null && userDocument.exists();
      Instant now = Instant.now();

      UserProfile existing = profileExists
          ? toUserProfile(uid, userDocument)
          : UserProfile.builder()
              .uid(uid)
              .visibility(ProfileVisibility.PUBLIC)
              .createdAt(now)
              .followerCount(0L)
              .followingCount(0L)
              .build();

      UserProfile updated = UserProfile.builder()
          .uid(uid)
          .displayName(request.getDisplayName().trim())
          .bio(normalizeOptional(request.getBio()))
          .avatarUrl(normalizeOptional(request.getAvatarUrl()))
          .visibility(request.getVisibility())
          .createdAt(existing.getCreatedAt() == null ? now : existing.getCreatedAt())
          .updatedAt(now)
          .followerCount(existing.getFollowerCount())
          .followingCount(existing.getFollowingCount())
          .build();

      writeProfile(userDocumentReference, updated, !profileExists);
      log.info("Updated canonical profile for authenticated user {}", uid);
      return toSelfResponse(updated);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw profileServiceUnavailable(e);
    } catch (ExecutionException e) {
      throw profileServiceUnavailable(e);
    }
  }

  private void requireFirestore() {
    if (firestore == null) {
      log.warn("Firestore not configured - cannot fetch user profile");
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
          "User profile service unavailable");
    }
  }

  private DocumentSnapshot getUserDocument(String uid)
      throws InterruptedException, ExecutionException {
    DocumentReference userDocRef = firestore.collection(usersCollection).document(uid);
    ApiFuture<DocumentSnapshot> userFuture = userDocRef.get();
    return userFuture.get();
  }

  private UserProfile toUserProfile(String uid, DocumentSnapshot userDocument) {
    Timestamp createdAt = userDocument.getTimestamp("createdAt");
    Timestamp updatedAt = userDocument.getTimestamp("updatedAt");
    Long followerCount = userDocument.getLong("followerCount");
    Long followingCount = userDocument.getLong("followingCount");

    return UserProfile.builder()
        .uid(uid)
        .displayName(userDocument.getString("displayName"))
        .bio(userDocument.getString("bio"))
        .avatarUrl(userDocument.getString("avatarUrl"))
        .visibility(ProfileVisibility.fromFirestoreValue(userDocument.getString("visibility")))
        .createdAt(createdAt == null ? null : createdAt.toDate().toInstant())
        .updatedAt(updatedAt == null ? null : updatedAt.toDate().toInstant())
        .followerCount(followerCount == null ? 0L : followerCount)
        .followingCount(followingCount == null ? 0L : followingCount)
        .build();
  }

  private SelfUserProfileResponse toSelfResponse(UserProfile profile) {
    return SelfUserProfileResponse.builder()
        .uid(profile.getUid())
        .displayName(profile.getDisplayName())
        .bio(profile.getBio())
        .avatarUrl(profile.getAvatarUrl())
        .visibility(profile.getVisibility())
        .createdAt(profile.getCreatedAt())
        .updatedAt(profile.getUpdatedAt())
        .followerCount(profile.getFollowerCount())
        .followingCount(profile.getFollowingCount())
        .build();
  }

  private void writeProfile(
      DocumentReference userDocumentReference, UserProfile profile, boolean initializeCounts)
      throws InterruptedException, ExecutionException {
    Map<String, Object> fields = new HashMap<>();
    fields.put("displayName", profile.getDisplayName());
    fields.put("bio", profile.getBio());
    fields.put("avatarUrl", profile.getAvatarUrl());
    fields.put("visibility", profile.getVisibility().name());
    fields.put("createdAt", Timestamp.ofTimeSecondsAndNanos(
        profile.getCreatedAt().getEpochSecond(), profile.getCreatedAt().getNano()));
    fields.put("updatedAt", Timestamp.ofTimeSecondsAndNanos(
        profile.getUpdatedAt().getEpochSecond(), profile.getUpdatedAt().getNano()));

    if (initializeCounts) {
      fields.put("followerCount", profile.getFollowerCount());
      fields.put("followingCount", profile.getFollowingCount());
    }

    userDocumentReference.set(fields, SetOptions.merge()).get();
  }

  private String normalizeOptional(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  private ResponseStatusException profileServiceUnavailable(Exception exception) {
    log.error("Error accessing canonical user profile in Firestore", exception);
    return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
        "User profile service unavailable", exception);
  }

  private List<RecipeResponse> fetchPublicRecipes(String uid) {
    if (recipeService == null) {
      return new ArrayList<>();
    }
    try {
      ApiFuture<QuerySnapshot> future = firestore.collection(recipesCollection)
          .whereEqualTo("userId", uid)
          .whereEqualTo("isPublic", true)
          .get();
      QuerySnapshot snapshot = future.get();
      List<RecipeResponse> recipes = new ArrayList<>();
      for (var doc : snapshot.getDocuments()) {
        Recipe recipe = doc.toObject(Recipe.class);
        if (recipe != null) {
          recipes.add(recipeService.mapToResponse(recipe));
        }
      }
      return recipes;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Interrupted while fetching public recipes for user {}", uid, e);
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "Failed to fetch public recipes",
          e);
    } catch (ExecutionException e) {
      log.error("Error fetching public recipes for user {}", uid, e);
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "Failed to fetch public recipes",
          e);
    }
  }

  private UserRecord getAuthUser(String uid) {
    if (firebaseAuth == null) {
      return null;
    }

    try {
      return firebaseAuth.getUser(uid);
    } catch (FirebaseAuthException e) {
      log.warn("Failed to resolve Firebase Auth user for {}: {}", uid, e.getMessage());
      return null;
    }
  }
}
