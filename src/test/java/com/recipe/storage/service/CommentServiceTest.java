package com.recipe.storage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import com.recipe.storage.dto.CommentResponse;
import com.recipe.storage.dto.CreateCommentRequest;
import com.recipe.storage.dto.PagedCommentResponse;
import com.recipe.storage.dto.UpdateCommentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for CommentService.
 */
public class CommentServiceTest {

  private CommentService commentService;

  @BeforeEach
  public void setUp() {
    commentService = new CommentService();
  }

  @Test
  public void testCreateGetUpdateDeleteCommentInMockStore() {
    CreateCommentRequest req = CreateCommentRequest.builder()
        .content("Great recipe!")
        .build();

    CommentResponse created = commentService.createComment("rec-201", req, "user-1");
    assertNotNull(created);
    assertEquals("Great recipe!", created.getContent());

    PagedCommentResponse comments = commentService.getComments("rec-201", 0, 10);
    assertEquals(1, comments.getTotalComments());
    assertFalse(comments.getComments().isEmpty());

    UpdateCommentRequest updateReq = UpdateCommentRequest.builder()
        .content("Updated recipe comment!")
        .build();
    CommentResponse updated = commentService.updateComment(created.getId(), updateReq, "user-1");
    assertEquals("Updated recipe comment!", updated.getContent());

    commentService.deleteComment(created.getId(), "user-1");
    PagedCommentResponse empty = commentService.getComments("rec-201", 0, 10);
    assertEquals(0, empty.getTotalComments());
  }
}
