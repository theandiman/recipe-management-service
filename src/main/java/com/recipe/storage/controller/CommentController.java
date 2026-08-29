package com.recipe.storage.controller;

import com.recipe.storage.dto.CommentResponse;
import com.recipe.storage.dto.CreateCommentRequest;
import com.recipe.storage.dto.PagedCommentResponse;
import com.recipe.storage.dto.UpdateCommentRequest;
import com.recipe.storage.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST controller for recipe comments and discussion threads.
 */
@Slf4j
@RestController
@RequestMapping
@RequiredArgsConstructor
@Validated
@Tag(name = "Recipe Comments",
    description = "APIs for recipe commenting and threaded discussions")
public class CommentController {

  private final CommentService commentService;

  /**
   * Post a comment or reply to a recipe.
   */
  @PostMapping("/api/recipes/{recipeId}/comments")
  @SecurityRequirement(name = "Firebase Auth")
  @Operation(summary = "Post a comment or reply to a recipe")
  public ResponseEntity<CommentResponse> createComment(
      @PathVariable String recipeId,
      @Valid @RequestBody CreateCommentRequest request,
      @Parameter(hidden = true)
      @RequestAttribute(name = "userId", required = false) String userId) {

    if (userId == null || userId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }

    log.info("User {} posting comment on recipe {}", userId, recipeId);
    CommentResponse response = commentService.createComment(recipeId, request, userId);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /**
   * Get paginated comment threads for a recipe.
   */
  @GetMapping("/api/recipes/{recipeId}/comments")
  @SecurityRequirements({})
  @Operation(summary = "Get paginated comment threads for a recipe")
  public ResponseEntity<PagedCommentResponse> getComments(
      @PathVariable String recipeId,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size) {

    log.info("Fetching comments for recipe {}", recipeId);
    PagedCommentResponse response = commentService.getComments(recipeId, page, size);
    return ResponseEntity.ok(response);
  }

  /**
   * Update an existing comment.
   */
  @PutMapping("/api/comments/{commentId}")
  @SecurityRequirement(name = "Firebase Auth")
  @Operation(summary = "Update an existing comment")
  public ResponseEntity<CommentResponse> updateComment(
      @PathVariable String commentId,
      @Valid @RequestBody UpdateCommentRequest request,
      @Parameter(hidden = true)
      @RequestAttribute(name = "userId", required = false) String userId) {

    if (userId == null || userId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }

    log.info("User {} updating comment {}", userId, commentId);
    CommentResponse response = commentService.updateComment(commentId, request, userId);
    return ResponseEntity.ok(response);
  }

  /**
   * Delete a comment.
   */
  @DeleteMapping("/api/comments/{commentId}")
  @SecurityRequirement(name = "Firebase Auth")
  @Operation(summary = "Delete a comment")
  public ResponseEntity<Void> deleteComment(
      @PathVariable String commentId,
      @Parameter(hidden = true)
      @RequestAttribute(name = "userId", required = false) String userId) {

    if (userId == null || userId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }

    log.info("User {} deleting comment {}", userId, commentId);
    commentService.deleteComment(commentId, userId);
    return ResponseEntity.noContent().build();
  }
}
