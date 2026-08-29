package com.recipe.storage.controller;

import com.recipe.storage.dto.PagedFollowResponse;
import com.recipe.storage.dto.UpdateUserProfileRequest;
import com.recipe.storage.dto.UserProfileResponse;
import com.recipe.storage.service.FollowService;
import com.recipe.storage.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST controller for public user profile operations.
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Validated
@Tag(name = "User Profiles", description = "APIs for accessing public user profile information")
public class UserProfileController {

  private final UserProfileService userProfileService;
  private final FollowService followService;

  /**
   * Get the public profile for a specific user.
   * Does NOT require authentication.
   *
   * @param uid The user's Firebase UID
   * @return The public user profile
   */
  @GetMapping("/{uid}/profile")
  @SecurityRequirements({})
  @Operation(
      summary = "Get a user's public profile",
      description = "Retrieves the public profile for a given user uid, including their display "
          + "name, bio, avatar URL, count of public recipes, follower/following counts, and "
          + "whether the authenticated caller follows this user. No authentication required; "
          + "isFollowedByCurrentUser is only populated when a valid token is provided.")
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "User profile retrieved successfully",
          content = @Content(schema = @Schema(implementation = UserProfileResponse.class))),
      @ApiResponse(
          responseCode = "404",
          description = "User not found",
          content = @Content)
  })
  public ResponseEntity<UserProfileResponse> getUserProfile(
      @Parameter(description = "User Firebase UID", required = true) @PathVariable String uid,
      @Parameter(hidden = true)
      @RequestAttribute(name = "userId", required = false) String currentUserId) {

    MDC.put("user.profile.uid", uid);
    try {
      log.info("Fetching public profile for user {}", uid);
      UserProfileResponse profile = userProfileService.getUserProfile(uid, currentUserId);
      return ResponseEntity.ok(profile);
    } finally {
      MDC.remove("user.profile.uid");
    }
  }

  /**
   * Get the authenticated caller's own profile.
   * Requires Firebase authentication.
   *
   * @param currentUserId The authenticated caller's Firebase UID
   * @return The authenticated user's profile
   */
  @GetMapping("/me/profile")
  @SecurityRequirement(name = "Firebase Auth")
  @Operation(
      summary = "Get authenticated user profile",
      description = "Retrieves the profile of the currently authenticated caller.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Profile retrieved successfully",
          content = @Content(schema = @Schema(implementation = UserProfileResponse.class))),
      @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
  })
  public ResponseEntity<UserProfileResponse> getMyProfile(
      @Parameter(hidden = true)
      @RequestAttribute(name = "userId", required = false) String currentUserId) {

    if (currentUserId == null || currentUserId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    log.info("Fetching self-profile for authenticated user {}", currentUserId);
    return ResponseEntity.ok(userProfileService.getUserProfile(currentUserId, currentUserId));
  }

  /**
   * Update the authenticated caller's profile.
   * Requires Firebase authentication.
   *
   * @param currentUserId The authenticated caller's Firebase UID
   * @param request       The profile fields to update
   * @return The updated user profile
   */
  @PutMapping("/me/profile")
  @SecurityRequirement(name = "Firebase Auth")
  @Operation(
      summary = "Update authenticated user profile",
      description = "Updates editable profile fields (displayName, bio, avatarUrl, visibility) "
          + "for the currently authenticated caller.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Profile updated successfully",
          content = @Content(schema = @Schema(implementation = UserProfileResponse.class))),
      @ApiResponse(responseCode = "400", description = "Invalid request payload",
          content = @Content),
      @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
  })
  public ResponseEntity<UserProfileResponse> updateMyProfile(
      @Parameter(hidden = true)
      @RequestAttribute(name = "userId", required = false) String currentUserId,
      @Valid @RequestBody UpdateUserProfileRequest request) {

    if (currentUserId == null || currentUserId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    log.info("Updating self-profile for user {}", currentUserId);
    UserProfileResponse updated = userProfileService.updateUserProfile(currentUserId, request);
    return ResponseEntity.ok(updated);
  }

  /**
   * Update a user profile by UID.
   * Requires Firebase authentication and caller UID matching path UID.
   *
   * @param uid           The user UID to update
   * @param currentUserId The authenticated caller's Firebase UID
   * @param request       The profile fields to update
   * @return The updated user profile
   */
  @PutMapping("/{uid}/profile")
  @SecurityRequirement(name = "Firebase Auth")
  @Operation(
      summary = "Update user profile",
      description = "Updates profile fields for the user matching path uid. "
          + "Only permitted if the caller is the owner of the profile.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Profile updated successfully",
          content = @Content(schema = @Schema(implementation = UserProfileResponse.class))),
      @ApiResponse(responseCode = "400", description = "Invalid request payload",
          content = @Content),
      @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
      @ApiResponse(responseCode = "403", description = "Forbidden: Cannot modify another profile",
          content = @Content)
  })
  public ResponseEntity<UserProfileResponse> updateUserProfile(
      @Parameter(description = "User Firebase UID", required = true) @PathVariable String uid,
      @Parameter(hidden = true)
      @RequestAttribute(name = "userId", required = false) String currentUserId,
      @Valid @RequestBody UpdateUserProfileRequest request) {

    if (currentUserId == null || currentUserId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    if (!currentUserId.equals(uid)) {
      log.warn("User {} attempted to modify profile of user {}", currentUserId, uid);
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,
          "Cannot modify another user's profile");
    }
    log.info("User {} updating profile for user {}", currentUserId, uid);
    UserProfileResponse updated = userProfileService.updateUserProfile(uid, request);
    return ResponseEntity.ok(updated);
  }

  /**
   * Follow a user.
   * Requires Firebase authentication.
   *
   * @param uid    The Firebase UID of the user to follow
   * @param userId The authenticated caller's Firebase UID (injected by auth filter)
   * @return 204 No Content on success
   */
  @PostMapping("/{uid}/follow")
  @SecurityRequirement(name = "Firebase Auth")
  @Operation(
      summary = "Follow a user",
      description = "Creates a follow relationship from the authenticated caller to the specified "
          + "user. Increments followerCount on the followed user and followingCount on the "
          + "caller. Idempotent: calling multiple times is safe. Requires authentication.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "204", description = "Followed successfully",
          content = @Content),
      @ApiResponse(responseCode = "400", description = "Cannot follow yourself",
          content = @Content),
      @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
  })
  public ResponseEntity<Void> followUser(
      @Parameter(description = "Firebase UID of the user to follow", required = true)
      @PathVariable String uid,
      @Parameter(hidden = true) @RequestAttribute("userId") String userId) {

    log.info("User {} following user {}", userId, uid);
    followService.followUser(userId, uid);
    return ResponseEntity.noContent().build();
  }

  /**
   * Unfollow a user.
   * Requires Firebase authentication.
   *
   * @param uid    The Firebase UID of the user to unfollow
   * @param userId The authenticated caller's Firebase UID (injected by auth filter)
   * @return 204 No Content on success
   */
  @DeleteMapping("/{uid}/follow")
  @SecurityRequirement(name = "Firebase Auth")
  @Operation(
      summary = "Unfollow a user",
      description = "Removes the follow relationship from the authenticated caller to the "
          + "specified user. Decrements followerCount on the unfollowed user and followingCount "
          + "on the caller. Idempotent: calling multiple times is safe. Requires authentication.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "204", description = "Unfollowed successfully",
          content = @Content),
      @ApiResponse(responseCode = "400", description = "Cannot unfollow yourself",
          content = @Content),
      @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
  })
  public ResponseEntity<Void> unfollowUser(
      @Parameter(description = "Firebase UID of the user to unfollow", required = true)
      @PathVariable String uid,
      @Parameter(hidden = true) @RequestAttribute("userId") String userId) {

    log.info("User {} unfollowing user {}", userId, uid);
    followService.unfollowUser(userId, uid);
    return ResponseEntity.noContent().build();
  }

  /**
   * Reconciles profile count statistics for the specified user.
   * Requires caller to be the profile owner.
   *
   * @param uid           Firebase UID of the user whose counts to reconcile
   * @param currentUserId caller's Firebase UID from authentication filter
   * @return reconciled UserProfileResponse
   */
  @PostMapping("/{uid}/reconcile")
  @Operation(
      summary = "Reconcile profile count statistics",
      description = "Recalculates actual followerCount, followingCount, and publicRecipeCount "
          + "from stored records and updates the profile document. Requires authentication.")
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "Profile counts reconciled successfully",
          content = @Content(schema = @Schema(implementation = UserProfileResponse.class))),
      @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
      @ApiResponse(responseCode = "403",
          description = "Forbidden: Cannot reconcile another user's profile", content = @Content)
  })
  public ResponseEntity<UserProfileResponse> reconcileProfile(
      @Parameter(description = "User Firebase UID", required = true) @PathVariable String uid,
      @Parameter(hidden = true) @RequestAttribute("userId") String currentUserId) {

    if (!currentUserId.equals(uid)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Cannot reconcile another user's profile");
    }

    UserProfileResponse response = userProfileService.reconcileProfileCounts(uid);
    return ResponseEntity.ok(response);
  }

  /**
   * Get a paginated list of users following the specified user.
   * Does NOT require authentication.
   *
   * @param uid       the Firebase UID of the user whose followers to retrieve
   * @param pageToken opaque cursor token from a previous response (null for first page)
   * @param pageSize  maximum number of users per page (default 20, min 1, max 100)
   * @return paginated list of followers
   */
  @GetMapping("/{uid}/followers")
  @SecurityRequirements({})
  @Operation(
      summary = "List followers of a user",
      description = "Returns a paginated list of users who follow the specified user. "
          + "No authentication required.")
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "Followers retrieved successfully",
          content = @Content(schema = @Schema(implementation = PagedFollowResponse.class))),
      @ApiResponse(responseCode = "400", description = "Invalid pageSize or pageToken",
          content = @Content)
  })
  public ResponseEntity<PagedFollowResponse> getFollowers(
      @Parameter(description = "User Firebase UID", required = true) @PathVariable String uid,
      @Parameter(description = "Cursor token from a previous response (omit for first page)")
      @RequestParam(name = "pageToken", required = false) String pageToken,
      @Parameter(description = "Page size (default: 20, min: 1, max: 100)")
      @RequestParam(name = "pageSize", defaultValue = "20") @Min(1) @Max(100) int pageSize,
      @Parameter(hidden = true)
      @RequestAttribute(name = "userId", required = false) String currentUserId) {

    MDC.put("user.profile.uid", uid);
    try {
      log.info("Fetching followers for user {}", uid);
      try {
        UserProfileResponse profile = userProfileService.getUserProfile(uid, currentUserId);
        boolean isOwner = currentUserId != null && currentUserId.equals(uid);
        if ("PRIVATE".equalsIgnoreCase(profile.getVisibility())
            && !isOwner && !profile.isFollowedByCurrentUser()) {
          throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Followers list is private");
        }
      } catch (ResponseStatusException e) {
        if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
          throw e;
        }
      }
      PagedFollowResponse response = followService.getFollowers(uid, pageToken, pageSize);
      return ResponseEntity.ok(response);
    } finally {
      MDC.remove("user.profile.uid");
    }
  }

  /**
   * Get a paginated list of users the specified user is following.
   * Does NOT require authentication.
   *
   * @param uid       the Firebase UID of the user whose following list to retrieve
   * @param pageToken opaque cursor token from a previous response (null for first page)
   * @param pageSize  maximum number of users per page (default 20, min 1, max 100)
   * @param currentUserId caller's Firebase UID if authenticated
   * @return paginated list of followed users
   */
  @GetMapping("/{uid}/following")
  @SecurityRequirements({})
  @Operation(
      summary = "List users that a user follows",
      description = "Returns a paginated list of users that the specified user follows. "
          + "No authentication required.")
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "Following list retrieved successfully",
          content = @Content(schema = @Schema(implementation = PagedFollowResponse.class))),
      @ApiResponse(responseCode = "400", description = "Invalid pageSize or pageToken",
          content = @Content),
      @ApiResponse(responseCode = "403", description = "Forbidden: Profile is private",
          content = @Content)
  })
  public ResponseEntity<PagedFollowResponse> getFollowing(
      @Parameter(description = "User Firebase UID", required = true) @PathVariable String uid,
      @Parameter(description = "Cursor token from a previous response (omit for first page)")
      @RequestParam(name = "pageToken", required = false) String pageToken,
      @Parameter(description = "Page size (default: 20, min: 1, max: 100)")
      @RequestParam(name = "pageSize", defaultValue = "20") @Min(1) @Max(100) int pageSize,
      @Parameter(hidden = true)
      @RequestAttribute(name = "userId", required = false) String currentUserId) {

    MDC.put("user.profile.uid", uid);
    try {
      log.info("Fetching following list for user {}", uid);
      try {
        UserProfileResponse profile = userProfileService.getUserProfile(uid, currentUserId);
        boolean isOwner = currentUserId != null && currentUserId.equals(uid);
        if ("PRIVATE".equalsIgnoreCase(profile.getVisibility())
            && !isOwner && !profile.isFollowedByCurrentUser()) {
          throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Following list is private");
        }
      } catch (ResponseStatusException e) {
        if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
          throw e;
        }
      }
      PagedFollowResponse response = followService.getFollowing(uid, pageToken, pageSize);
      return ResponseEntity.ok(response);
    } finally {
      MDC.remove("user.profile.uid");
    }
  }

  /**
   * Search public user profiles.
   */
  @GetMapping("/search")
  @SecurityRequirements({})
  @Operation(summary = "Search public user profiles")
  public ResponseEntity<com.recipe.storage.dto.PagedUserSearchResponse> searchUsers(
      @RequestParam(name = "q", defaultValue = "") String q,
      @RequestParam(name = "pageToken", required = false) String pageToken,
      @RequestParam(name = "pageSize", defaultValue = "20") @Min(1) @Max(100) int pageSize,
      @Parameter(hidden = true)
      @RequestAttribute(name = "userId", required = false) String currentUserId) {

    log.info("Searching users with query '{}'", q);
    com.recipe.storage.dto.PagedUserSearchResponse response =
        userProfileService.searchUsers(q, pageToken, pageSize, currentUserId);
    return ResponseEntity.ok(response);
  }

  /**
   * Get featured top creators.
   */
  @GetMapping("/featured")
  @SecurityRequirements({})
  @Operation(summary = "Get featured top creators")
  public ResponseEntity<com.recipe.storage.dto.FeaturedCreatorsResponse> getFeaturedCreators(
      @RequestParam(name = "limit", defaultValue = "10") @Min(1) @Max(50) int limit,
      @Parameter(hidden = true)
      @RequestAttribute(name = "userId", required = false) String currentUserId) {

    log.info("Fetching featured creators (limit={})", limit);
    com.recipe.storage.dto.FeaturedCreatorsResponse response =
        userProfileService.getFeaturedCreators(limit, currentUserId);
    return ResponseEntity.ok(response);
  }
}
