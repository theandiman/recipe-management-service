package com.recipe.storage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Paginated list of user profiles matching search criteria.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Paginated list of user profiles matching search criteria")
public class PagedUserSearchResponse {

  private List<UserProfileResponse> users;
  private boolean hasMore;
}
