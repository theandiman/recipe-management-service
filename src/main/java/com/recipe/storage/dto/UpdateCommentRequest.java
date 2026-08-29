package com.recipe.storage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for updating an existing comment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for updating an existing comment")
public class UpdateCommentRequest {

  @NotBlank(message = "Comment content cannot be blank")
  @Size(max = 2000, message = "Comment content cannot exceed 2000 characters")
  @Schema(description = "Updated comment content", example = "Updated text...",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String content;
}
