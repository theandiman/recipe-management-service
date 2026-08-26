package com.recipe.storage.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.SetOptions;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.recipe.shared.model.Recipe;
import com.recipe.storage.dto.RecipeResponse;
import com.recipe.storage.dto.UpdateUserProfileRequest;
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
    if (firestore == null || firestore.collection(usersCollection) == null) {
      log.warn("Firestore not configured - cannot fetch user profile");
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
          "User profile service unavailable");
    }

    try {
      DocumentReference userDocRef = firestore.collection(usersCollection).document(uid);
      ApiFuture<DocumentSnapshot> userFuture = userDocRef.get();
      DocumentSnapshot userDocument = userFuture.get();

      boolean hasFirestoreProfile = userDocument != null && userDocument.exists();

      String displayName = null;
      final String bio;
      String avatarUrl = null;
      String visibility = "PUBLIC";
      String createdAt = null;
      String updatedAt = null;
      Long rawFollowerCount = null;
      Long rawFollowingCount = null;

      if (hasFirestoreProfile) {
        displayName = userDocument.getString("displayName");
        bio = userDocument.getString("bio");
        avatarUrl = userDocument.getString("avatarUrl");
        String vis = userDocument.getString("visibility");
        if (vis != null && !vis.isBlank()) {
          visibility = vis;
        }
        createdAt = userDocument.getString("createdAt");
        updatedAt = userDocument.getString("updatedAt");
        rawFollowerCount = userDocument.getLong("followerCount");
        rawFollowingCount = userDocument.getLong("followingCount");
      } else {
        bio = null;
      }

      UserRecord authUser = null;
      boolean needsAuthFallback = !hasFirestoreProfile
          || displayName == null || displayName.isBlank()
          || avatarUrl == null || avatarUrl.isBlank();

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

      boolean isOwner = currentUserId != null && currentUserId.equals(uid);
      boolean isFollowedByCurrentUser = currentUserId != null
          && followService != null
          && followService.isFollowing(currentUserId, uid);

      boolean isPrivate = "PRIVATE".equalsIgnoreCase(visibility);
      boolean canAccessPrivateData = !isPrivate || isOwner || isFollowedByCurrentUser;

      String finalBio = canAccessPrivateData ? bio : null;
      List<RecipeResponse> publicRecipes = canAccessPrivateData
          ? fetchPublicRecipes(uid)
          : new ArrayList<>();
      long publicRecipeCount = publicRecipes.size();

      log.info("Retrieved profile for user {} (visibility={}, canAccessPrivateData={})",
          uid, visibility, canAccessPrivateData);

      return UserProfileResponse.builder()
          .uid(uid)
          .displayName(displayName)
          .bio(finalBio)
          .avatarUrl(avatarUrl)
          .visibility(visibility)
          .publicRecipeCount(publicRecipeCount)
          .publicRecipes(publicRecipes)
          .followerCount(followerCount)
          .followingCount(followingCount)
          .isFollowedByCurrentUser(isFollowedByCurrentUser)
          .createdAt(createdAt)
          .updatedAt(updatedAt)
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

  /**
   * Update the profile for a given user uid.
   *
   * @param uid     The Firebase user ID of the profile to update
   * @param request The updated profile fields
   * @return The updated user profile response
   */
  public UserProfileResponse updateUserProfile(String uid, UpdateUserProfileRequest request) {
    if (firestore == null || firestore.collection(usersCollection) == null) {
      log.warn("Firestore not configured - cannot update user profile");
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
          "User profile service unavailable");
    }

    try {
      Map<String, Object> updates = new HashMap<>();

      if (request.getDisplayName() != null) {
        updates.put("displayName", request.getDisplayName().trim());
      }
      if (request.getBio() != null) {
        updates.put("bio", request.getBio().trim());
      }
      if (request.getAvatarUrl() != null) {
        updates.put("avatarUrl", request.getAvatarUrl().trim());
      }
      if (request.getVisibility() != null) {
        updates.put("visibility", request.getVisibility());
      }

      String now = Instant.now().toString();
      updates.put("updatedAt", now);

      final DocumentReference userDocRef = firestore.collection(usersCollection).document(uid);
      ApiFuture<WriteResult> writeFuture = userDocRef.set(updates, SetOptions.merge());
      writeFuture.get();

      boolean hasDisplayName = request.getDisplayName() != null
          && !request.getDisplayName().isBlank();
      if (firebaseAuth != null && hasDisplayName) {
        try {
          UserRecord.UpdateRequest authUpdate = new UserRecord.UpdateRequest(uid)
              .setDisplayName(request.getDisplayName().trim());
          firebaseAuth.updateUser(authUpdate);
        } catch (FirebaseAuthException e) {
          log.warn("Failed to sync displayName to Auth for user {}: {}", uid, e.getMessage());
        }
      }

      log.info("Updated profile for user {}", uid);
      return getUserProfile(uid, uid);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Interrupted while updating user profile in Firestore", e);
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "User profile service unavailable", e);
    } catch (ExecutionException e) {
      log.error("Error updating user profile in Firestore", e);
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "User profile service unavailable", e);
    }
  }
}

