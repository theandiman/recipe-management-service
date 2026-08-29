package com.recipe.storage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Paginated list of ratings with summary statistics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Paginated list of ratings with summary statistics")
public class PagedRatingResponse {

  private double averageRating;
  private int ratingCount;
  private RatingDistribution distribution;
  private List<RatingResponse> ratings;
  private boolean hasMore;
}
