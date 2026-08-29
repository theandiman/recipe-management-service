package com.recipe.storage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for posting a new comment or reply.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for posting a new comment or reply")
public class CreateCommentRequest {

  @NotBlank(message = "Comment content cannot be blank")
  @Size(max = 2000, message = "Comment content cannot exceed 2000 characters")
  @Schema(description = "Comment body content", example = "Loved this recipe!",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String content;

  @Schema(description = "Optional parent comment ID if replying to an existing comment",
      example = "comment-123")
  private String parentId;
}
