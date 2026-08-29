package com.recipe.storage.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recipe.storage.dto.CommentResponse;
import com.recipe.storage.dto.CreateCommentRequest;
import com.recipe.storage.dto.PagedCommentResponse;
import com.recipe.storage.dto.UpdateCommentRequest;
import com.recipe.storage.service.CommentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Unit tests for CommentController.
 */
public class CommentControllerTest {

  private CommentService commentService;
  private CommentController commentController;

  @BeforeEach
  public void setUp() {
    commentService = mock(CommentService.class);
    commentController = new CommentController(commentService);
  }

  @Test
  public void testCreateComment() {
    CreateCommentRequest req = CreateCommentRequest.builder().content("Tasty").build();
    CommentResponse expected = CommentResponse.builder().id("c-1").content("Tasty").build();

    when(commentService.createComment(eq("rec-1"), any(), eq("user-1"))).thenReturn(expected);

    ResponseEntity<CommentResponse> res = commentController.createComment("rec-1", req, "user-1");
    assertEquals(HttpStatus.CREATED, res.getStatusCode());
    assertEquals(expected, res.getBody());
  }

  @Test
  public void testGetComments() {
    PagedCommentResponse expected = PagedCommentResponse.builder().build();
    when(commentService.getComments("rec-1", 0, 20)).thenReturn(expected);

    ResponseEntity<PagedCommentResponse> res = commentController.getComments("rec-1", 0, 20);
    assertEquals(HttpStatus.OK, res.getStatusCode());
    assertEquals(expected, res.getBody());
  }

  @Test
  public void testUpdateComment() {
    UpdateCommentRequest req = UpdateCommentRequest.builder().content("Updated").build();
    CommentResponse expected = CommentResponse.builder().id("c-1").content("Updated").build();

    when(commentService.updateComment(eq("c-1"), any(), eq("user-1"))).thenReturn(expected);

    ResponseEntity<CommentResponse> res = commentController.updateComment("c-1", req, "user-1");
    assertEquals(HttpStatus.OK, res.getStatusCode());
    assertEquals(expected, res.getBody());
  }

  @Test
  public void testDeleteComment() {
    ResponseEntity<Void> res = commentController.deleteComment("c-1", "user-1");
    assertEquals(HttpStatus.NO_CONTENT, res.getStatusCode());
    verify(commentService).deleteComment("c-1", "user-1");
  }
}
