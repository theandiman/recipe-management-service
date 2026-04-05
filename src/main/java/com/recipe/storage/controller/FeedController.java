package com.recipe.storage.controller;

import com.recipe.storage.dto.PagedRecipeResponse;
import com.recipe.storage.service.RecipeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the personalised social feed.
 */
@Slf4j
@RestController
@RequestMapping("/api/feed")
@RequiredArgsConstructor
@Tag(name = "Feed", description = "APIs for the personalised social recipe feed")
@SecurityRequirement(name = "Firebase Auth")
@Validated
public class FeedController {

  private final RecipeService recipeService;

  /**
   * Get the personalised feed for the authenticated user.
   *
   * <p>Returns public recipes from users the caller follows, ordered by creation date
   * (newest first). Supports cursor-based pagination via {@code pageToken} and {@code size}.
   * Returns an empty list when the caller follows no one or followed users have no public
   * recipes.
   *
   * @param pageToken cursor token from a previous response (null/omitted for first page)
   * @param size      number of recipes per page (default 20, min 1, max 100)
   * @param userId    the authenticated user's Firebase UID (injected by auth filter)
   * @return paginated feed of public recipes from followed users
   */
  @GetMapping
  @Operation(
      summary = "Get personalised feed",
      description = "Returns public recipes from users the authenticated user follows, "
          + "ordered by creation date (newest first). "
          + "Returns an empty list when following no one or followed users have no "
          + "public recipes. Requires Firebase authentication token in Authorization header.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Feed retrieved successfully",
          content = @Content(
              schema = @Schema(implementation = PagedRecipeResponse.class))),
      @ApiResponse(responseCode = "400",
          description = "Invalid pagination parameters (size < 1, size > 100, "
              + "or malformed pageToken)",
          content = @Content),
      @ApiResponse(responseCode = "401",
          description = "Unauthorized - invalid or missing Firebase token",
          content = @Content)
  })
  public ResponseEntity<PagedRecipeResponse> getFeed(
      @Parameter(description = "Cursor token from a previous response (omit for first page)")
      @RequestParam(name = "pageToken", required = false) String pageToken,
      @Parameter(description = "Page size (default: 20, min: 1, max: 100)")
      @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size,
      @Parameter(hidden = true) @RequestAttribute("userId") String userId) {

    log.info("Fetching feed for user {} (pageToken={}, size={})", userId, pageToken, size);
    PagedRecipeResponse response = recipeService.getFeed(userId, pageToken, size);
    return ResponseEntity.ok(response);
  }
}
