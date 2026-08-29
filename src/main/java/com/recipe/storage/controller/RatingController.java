package com.recipe.storage.controller;

import com.recipe.storage.dto.CreateRatingRequest;
import com.recipe.storage.dto.PagedRatingResponse;
import com.recipe.storage.dto.RatingResponse;
import com.recipe.storage.service.RatingService;
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
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST controller for recipe ratings and reviews.
 */
@Slf4j
@RestController
@RequestMapping("/api/recipes/{recipeId}/ratings")
@RequiredArgsConstructor
@Validated
@Tag(name = "Recipe Ratings",
    description = "APIs for submitting and viewing recipe ratings & reviews")
public class RatingController {

  private final RatingService ratingService;

  /**
   * Submit or update rating & review for a recipe.
   */
  @PostMapping
  @SecurityRequirement(name = "Firebase Auth")
  @Operation(summary = "Submit or update rating & review for a recipe")
  public ResponseEntity<RatingResponse> saveRating(
      @PathVariable String recipeId,
      @Valid @RequestBody CreateRatingRequest request,
      @Parameter(hidden = true)
      @RequestAttribute(name = "userId", required = false) String userId) {

    if (userId == null || userId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }

    log.info("User {} rating recipe {} with score {}", userId, recipeId, request.getScore());
    RatingResponse response = ratingService.saveRating(recipeId, request, userId);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /**
   * Get paginated ratings and reviews for a recipe.
   */
  @GetMapping
  @SecurityRequirements({})
  @Operation(summary = "Get paginated ratings and reviews for a recipe")
  public ResponseEntity<PagedRatingResponse> getRatings(
      @PathVariable String recipeId,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "10") int size,
      @RequestParam(name = "sort", defaultValue = "newest") String sort) {

    log.info("Fetching ratings for recipe {}", recipeId);
    PagedRatingResponse response = ratingService.getRatings(recipeId, page, size, sort);
    return ResponseEntity.ok(response);
  }

  /**
   * Delete user's rating for a recipe.
   */
  @DeleteMapping("/me")
  @SecurityRequirement(name = "Firebase Auth")
  @Operation(summary = "Delete user's rating for a recipe")
  public ResponseEntity<Void> deleteRating(
      @PathVariable String recipeId,
      @Parameter(hidden = true)
      @RequestAttribute(name = "userId", required = false) String userId) {

    if (userId == null || userId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }

    log.info("User {} deleting rating for recipe {}", userId, recipeId);
    ratingService.deleteRating(recipeId, userId);
    return ResponseEntity.noContent().build();
  }
}
