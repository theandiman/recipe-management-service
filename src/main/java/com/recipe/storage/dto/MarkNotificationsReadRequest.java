package com.recipe.storage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for marking notifications as read.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for marking notifications as read")
public class MarkNotificationsReadRequest {

  @Schema(description = "List of notification IDs to mark as read, or empty to mark all as read")
  private List<String> notificationIds;
}
