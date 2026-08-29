package com.recipe.storage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for submitting or updating a recipe rating and review.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request body for submitting or updating a recipe rating and review")
public class CreateRatingRequest {

  @NotNull(message = "Score is required")
  @Min(value = 1, message = "Score must be at least 1")
  @Max(value = 5, message = "Score must be at most 5")
  @Schema(description = "Rating score from 1 to 5 stars", example = "5",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private Integer score;

  @Size(max = 1000, message = "Review text cannot exceed 1000 characters")
  @Schema(description = "Optional review text", example = "Loved this recipe!")
  private String reviewText;
}
