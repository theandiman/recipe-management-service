package com.recipe.storage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import com.recipe.storage.dto.CreateRatingRequest;
import com.recipe.storage.dto.PagedRatingResponse;
import com.recipe.storage.dto.RatingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for RatingService.
 */
public class RatingServiceTest {

  private RatingService ratingService;

  @BeforeEach
  public void setUp() {
    ratingService = new RatingService();
  }

  @Test
  public void testSaveAndGetRatingInMockStore() {
    CreateRatingRequest req = CreateRatingRequest.builder()
        .score(5)
        .reviewText("Amazing recipe!")
        .build();

    RatingResponse saved = ratingService.saveRating("rec-101", req, "user-1");
    assertNotNull(saved);
    assertEquals("rec-101", saved.getRecipeId());
    assertEquals(5, saved.getScore());

    PagedRatingResponse paged = ratingService.getRatings("rec-101", 0, 10, "newest");
    assertNotNull(paged);
    assertEquals(1, paged.getRatingCount());
    assertEquals(5.0, paged.getAverageRating());
    assertFalse(paged.getRatings().isEmpty());
  }

  @Test
  public void testDeleteRatingInMockStore() {
    CreateRatingRequest req = CreateRatingRequest.builder()
        .score(4)
        .reviewText("Good recipe!")
        .build();

    ratingService.saveRating("rec-102", req, "user-2");
    ratingService.deleteRating("rec-102", "user-2");

    PagedRatingResponse paged = ratingService.getRatings("rec-102", 0, 10, "newest");
    assertEquals(0, paged.getRatingCount());
  }
}
