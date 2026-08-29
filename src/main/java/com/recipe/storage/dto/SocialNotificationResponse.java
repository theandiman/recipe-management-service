package com.recipe.storage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Individual social notification item.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Individual social notification item")
public class SocialNotificationResponse {

  private String id;
  private String recipientUid;
  private String actorUid;
  private String actorName;
  private String actorAvatarUrl;
  private String eventType;
  private String targetRecipeId;
  private String targetRecipeName;
  private String contentSnippet;
  private Boolean isRead;
  private String createdAt;
}
