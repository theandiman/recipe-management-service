package com.recipe.storage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO representing a user in a followers or following list.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A user in a followers or following list")
public class FollowUserResponse {

  @Schema(description = "Firebase UID of the user", example = "abc123")
  private String uid;

  @Schema(description = "Display name of the user", example = "Alice")
  private String displayName;

  @Schema(description = "Profile photo URL of the user")
  private String photoUrl;

  @Schema(description = "Avatar URL of the user", example = "https://example.com/avatar.jpg")
  private String avatarUrl;
}
