package com.recipe.storage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Complete canonical profile returned only to its authenticated owner.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Complete canonical profile for the authenticated user")
public class SelfUserProfileResponse {

  @Schema(description = "Firebase UID that owns this profile")
  private String uid;

  @Schema(description = "Configured display name")
  private String displayName;

  @Schema(description = "Configured biography", nullable = true)
  private String bio;

  @Schema(description = "Configured avatar URL", nullable = true)
  private String avatarUrl;

  @Schema(description = "Profile visibility")
  private ProfileVisibility visibility;

  @Schema(description = "When the canonical document was created", format = "date-time")
  private Instant createdAt;

  @Schema(description = "When editable profile fields were last updated", format = "date-time")
  private Instant updatedAt;

  @Schema(description = "Derived number of followers")
  private long followerCount;

  @Schema(description = "Derived number of profiles followed")
  private long followingCount;
}
