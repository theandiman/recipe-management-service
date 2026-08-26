package com.recipe.storage.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for a user's public profile.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {

  private String uid;
  private String displayName;
  private String bio;
  private String avatarUrl;
  @Builder.Default
  private String visibility = "PUBLIC";
  private long publicRecipeCount;
  private long followerCount;
  private long followingCount;
  @JsonProperty("isFollowedByCurrentUser")
  private boolean isFollowedByCurrentUser;
  private List<RecipeResponse> publicRecipes;
  private String createdAt;
  private String updatedAt;
}
