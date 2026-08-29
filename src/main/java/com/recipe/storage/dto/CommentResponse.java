package com.recipe.storage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Comment or nested reply details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Comment or nested reply details")
public class CommentResponse {

  private String id;
  private String recipeId;
  private String userId;
  private String authorName;
  private String authorAvatarUrl;
  private String content;
  private String parentId;
  private Integer likeCount;
  private String createdAt;
  private String updatedAt;
  private List<CommentResponse> replies;
}
