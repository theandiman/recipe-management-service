package com.recipe.storage.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Count of ratings per star score.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Count of ratings per star score")
public class RatingDistribution {

  @JsonProperty("1")
  @Builder.Default
  private int star1 = 0;

  @JsonProperty("2")
  @Builder.Default
  private int star2 = 0;

  @JsonProperty("3")
  @Builder.Default
  private int star3 = 0;

  @JsonProperty("4")
  @Builder.Default
  private int star4 = 0;

  @JsonProperty("5")
  @Builder.Default
  private int star5 = 0;
}
