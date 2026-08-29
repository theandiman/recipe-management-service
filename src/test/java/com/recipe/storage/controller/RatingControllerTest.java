package com.recipe.storage.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recipe.storage.dto.CreateRatingRequest;
import com.recipe.storage.dto.PagedRatingResponse;
import com.recipe.storage.dto.RatingResponse;
import com.recipe.storage.service.RatingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Unit tests for RatingController.
 */
public class RatingControllerTest {

  private RatingService ratingService;
  private RatingController ratingController;

  @BeforeEach
  public void setUp() {
    ratingService = mock(RatingService.class);
    ratingController = new RatingController(ratingService);
  }

  @Test
  public void testSaveRating() {
    CreateRatingRequest req = CreateRatingRequest.builder().score(5).build();
    RatingResponse expected = RatingResponse.builder().id("r-1").score(5).build();

    when(ratingService.saveRating(eq("rec-1"), any(), eq("user-1"))).thenReturn(expected);

    ResponseEntity<RatingResponse> res = ratingController.saveRating("rec-1", req, "user-1");
    assertEquals(HttpStatus.CREATED, res.getStatusCode());
    assertEquals(expected, res.getBody());
  }

  @Test
  public void testGetRatings() {
    PagedRatingResponse expected = PagedRatingResponse.builder().build();
    when(ratingService.getRatings("rec-1", 0, 10, "newest")).thenReturn(expected);

    ResponseEntity<PagedRatingResponse> res = ratingController.getRatings("rec-1", 0, 10, "newest");
    assertEquals(HttpStatus.OK, res.getStatusCode());
    assertEquals(expected, res.getBody());
  }

  @Test
  public void testDeleteRating() {
    ResponseEntity<Void> res = ratingController.deleteRating("rec-1", "user-1");
    assertEquals(HttpStatus.NO_CONTENT, res.getStatusCode());
    verify(ratingService).deleteRating("rec-1", "user-1");
  }
}
