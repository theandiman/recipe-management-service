package com.recipe.storage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Paginated social notifications response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Paginated social notifications response")
public class PagedNotificationResponse {

  private int unreadCount;
  private List<SocialNotificationResponse> notifications;
  private boolean hasMore;
}
