package com.recipe.storage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Individual rating and review item.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Individual rating and review item")
public class RatingResponse {

  private String id;
  private String recipeId;
  private String userId;
  private String authorName;
  private String authorAvatarUrl;
  private Integer score;
  private String reviewText;
  private String createdAt;
  private String updatedAt;
}
