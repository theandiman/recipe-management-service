package com.recipe.storage.controller;

import com.recipe.storage.dto.PagedFollowResponse;
import com.recipe.storage.dto.SelfUserProfileResponse;
import com.recipe.storage.dto.UpdateUserProfileRequest;
import com.recipe.storage.dto.UserProfileResponse;
import com.recipe.storage.dto.ValidationErrorResponse;
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

/**
 * REST controller for public and self-service user profile operations.
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Validated
@Tag(name = "User Profiles", description = "APIs for public and authenticated self profiles")
public class UserProfileController {

  private final UserProfileService userProfileService;
  private final FollowService followService;

  /**
   * Get the complete canonical profile for the authenticated user.
   *
   * @param userId Firebase UID injected by the authentication filter
   * @return owner-only canonical profile
   */
  @GetMapping("/me/profile")
  @SecurityRequirement(name = "Firebase Auth")
  @Operation(
      summary = "Get my profile",
      description = "Returns the authenticated user's complete canonical profile, including "
          + "visibility, timestamps, and derived follow counts. If no Firestore profile exists, "
          + "it is safely bootstrapped; Firebase Auth supplies optional display-name and avatar "
          + "metadata when available.")
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "Canonical profile retrieved successfully",
          content = @Content(schema = @Schema(implementation = SelfUserProfileResponse.class))),
      @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
      @ApiResponse(responseCode = "503", description = "Profile service unavailable",
          content = @Content)
  })
  public ResponseEntity<SelfUserProfileResponse> getSelfProfile(
      @Parameter(hidden = true) @RequestAttribute("userId") String userId) {
    return ResponseEntity.ok(userProfileService.getSelfProfile(userId));
  }

  /**
   * Rebuild the authenticated user's profile metadata and follow counters.
   *
   * @param userId Firebase UID injected by the authentication filter
   * @return repaired owner-only canonical profile
   */
  @PostMapping("/me/profile/repair")
  @SecurityRequirement(name = "Firebase Auth")
  @Operation(
      summary = "Repair my profile",
      description = "Safely backfills missing canonical profile metadata and rebuilds the "
          + "authenticated user's follower and following counts from that user's follow indexes. "
          + "This operation is idempotent and never deletes recipes, follows, or avatar data.")
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "Canonical profile repaired successfully",
          content = @Content(schema = @Schema(implementation = SelfUserProfileResponse.class))),
      @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
  })
  public ResponseEntity<SelfUserProfileResponse> repairSelfProfile(
      @Parameter(hidden = true) @RequestAttribute("userId") String userId) {
    return ResponseEntity.ok(userProfileService.repairSelfProfile(userId));
  }

  /**
   * Replace editable fields on the authenticated user's canonical profile.
   *
   * @param request validated editable profile fields
   * @param userId Firebase UID injected by the authentication filter
   * @return updated owner-only canonical profile
   */
  @PutMapping("/me/profile")
  @SecurityRequirement(name = "Firebase Auth")
  @Operation(
      summary = "Update my profile",
      description = "Replaces editable fields on the authenticated user's canonical profile. "
          + "UIDs, timestamps, and follow counts are service-owned and rejected when supplied.")
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "Canonical profile updated successfully",
          content = @Content(schema = @Schema(implementation = SelfUserProfileResponse.class))),
      @ApiResponse(
          responseCode = "400",
          description = "Invalid or unsupported profile fields",
          content = @Content(schema = @Schema(implementation = ValidationErrorResponse.class))),
      @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
  })
  public ResponseEntity<SelfUserProfileResponse> updateSelfProfile(
      @Valid @RequestBody UpdateUserProfileRequest request,
      @Parameter(hidden = true) @RequestAttribute("userId") String userId) {
    return ResponseEntity.ok(userProfileService.updateSelfProfile(userId, request));
  }

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
          + "isFollowedByCurrentUser is only populated when a valid token is provided. Profiles "
          + "with PRIVATE visibility retain public recipe compatibility but omit personal fields "
          + "and derived follow counts.")
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
      @RequestParam(name = "pageSize", defaultValue = "20") @Min(1) @Max(100) int pageSize) {

    MDC.put("user.profile.uid", uid);
    try {
      log.info("Fetching followers for user {}", uid);
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
          content = @Content)
  })
  public ResponseEntity<PagedFollowResponse> getFollowing(
      @Parameter(description = "User Firebase UID", required = true) @PathVariable String uid,
      @Parameter(description = "Cursor token from a previous response (omit for first page)")
      @RequestParam(name = "pageToken", required = false) String pageToken,
      @Parameter(description = "Page size (default: 20, min: 1, max: 100)")
      @RequestParam(name = "pageSize", defaultValue = "20") @Min(1) @Max(100) int pageSize) {

    MDC.put("user.profile.uid", uid);
    try {
      log.info("Fetching following list for user {}", uid);
      PagedFollowResponse response = followService.getFollowing(uid, pageToken, pageSize);
      return ResponseEntity.ok(response);
    } finally {
      MDC.remove("user.profile.uid");
    }
  }
}
