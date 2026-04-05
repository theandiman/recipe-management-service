package com.recipe.storage.controller;

import com.recipe.storage.dto.CreateRecipeRequest;
import com.recipe.storage.dto.PagedRecipeResponse;
import com.recipe.storage.dto.RecipeResponse;
import com.recipe.storage.dto.UpdateSharingRequest;
import com.recipe.storage.service.RecipeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for recipe storage operations.
 */
@Slf4j
@RestController
@RequestMapping("/api/recipes")
@RequiredArgsConstructor
@Tag(name = "Recipe Storage", description = "APIs for storing and retrieving user recipes")
@SecurityRequirement(name = "Firebase Auth")
@Validated
public class RecipeController {

    private final RecipeService recipeService;

    /**
     * Save a new recipe.
     * Requires Firebase authentication.
     *
     * @param request The recipe creation request
     * @param userId  The authenticated user's Firebase UID (injected by auth
     *                filter)
     * @return The saved recipe
     */
    @PostMapping
    @Operation(summary = "Save a new recipe", description = "Creates and stores a new recipe for the authenticated user. "
            + "Requires Firebase authentication token in Authorization header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Recipe created successfully", content = @Content(schema = @Schema(implementation = RecipeResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body (missing required fields or validation errors)", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing Firebase token", content = @Content)
    })
    public ResponseEntity<RecipeResponse> saveRecipe(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Recipe details to save", required = true, content = @Content(schema = @Schema(implementation = CreateRecipeRequest.class))) @Valid @RequestBody CreateRecipeRequest request,
            @Parameter(hidden = true) @RequestAttribute("userId") String userId) {

        log.info("Saving recipe '{}' for user {}", request.getTitle(), userId);
        RecipeResponse response = recipeService.saveRecipe(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get all recipes for the authenticated user.
     * Requires Firebase authentication.
     *
     * @param userId The authenticated user's Firebase UID (injected by auth filter)
     * @return List of recipes belonging to the user
     */
    @GetMapping
    @Operation(summary = "Get all user recipes", description = "Retrieves all recipes owned by the authenticated user, "
            + "ordered by creation date (newest first). "
            + "Requires Firebase authentication token in Authorization header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Recipes retrieved successfully (returns empty array if none found)", content = @Content(schema = @Schema(implementation = RecipeResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing Firebase token", content = @Content)
    })
    public ResponseEntity<List<RecipeResponse>> getUserRecipes(
            @Parameter(hidden = true) @RequestAttribute("userId") String userId) {

        log.info("Fetching recipes for user {}", userId);
        List<RecipeResponse> recipes = recipeService.getUserRecipes(userId);
        return ResponseEntity.ok(recipes);
    }

    /**
     * Get public recipes with optional pagination.
     * Does NOT require authentication.
     *
     * @param pageToken Cursor token from a previous response (null/omitted for first page)
     * @param size Number of recipes per page (default 20, max 100)
     * @return Paginated public recipes
     */
    @GetMapping("/public")
    @Operation(summary = "Get public recipes", description = "Retrieves recipes that have been "
            + "marked as public, ordered by creation date (newest first). Supports cursor-based "
            + "pagination via 'pageToken' and 'size' query parameters. No authentication required.")
    @io.swagger.v3.oas.annotations.security.SecurityRequirements({})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Public recipes retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PagedRecipeResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "Invalid pagination parameters (size < 1, size > 100, "
                            + "or malformed pageToken)",
                    content = @Content)
    })
    public ResponseEntity<PagedRecipeResponse> getPublicRecipes(
            @Parameter(description = "Cursor token from a previous response (omit for first page)")
            @RequestParam(name = "pageToken", required = false) String pageToken,
            @Parameter(description = "Page size (default: 20, min: 1, max: 100)")
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        log.info("Fetching public recipes (pageToken={}, size={})", pageToken, size);
        PagedRecipeResponse response = recipeService.getPublicRecipes(pageToken, size);
        return ResponseEntity.ok(response);
    }

    /**
     * Get a public recipe by ID without authentication.
     * Returns 404 if recipe does not exist or is not public.
     *
     * @param recipeId The recipe ID
     * @return The recipe if it exists and is public
     */
    @GetMapping("/{recipeId}/public")
    @SecurityRequirements({})
    @Operation(summary = "Get a single public recipe", description = "Retrieves a recipe by its ID without authentication. "
            + "Returns 404 if the recipe does not exist or is not public.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Public recipe retrieved successfully", content = @Content(schema = @Schema(implementation = RecipeResponse.class))),
            @ApiResponse(responseCode = "404", description = "Recipe not found or is not public", content = @Content)
    })
    public ResponseEntity<RecipeResponse> getPublicRecipe(
            @Parameter(description = "Recipe ID", required = true) @PathVariable String recipeId) {

        MDC.put("recipe.id", recipeId);
        try {
            log.info("Fetching public recipe {}", recipeId);
            RecipeResponse recipe = recipeService.getPublicRecipe(recipeId);
            return ResponseEntity.ok(recipe);
        } finally {
            MDC.remove("recipe.id");
        }
    }

    /**
     * Get the paginated list of saved (bookmarked) recipes for the authenticated user.
     * Requires Firebase authentication.
     *
     * @param pageToken Cursor token from a previous response (null/omitted for first page)
     * @param size      Number of recipes per page (default 20, max 100)
     * @param userId    The authenticated user's Firebase UID (injected by auth filter)
     * @return Paginated saved recipes
     */
    @GetMapping("/saved")
    @Operation(summary = "Get saved recipes", description = "Returns the paginated list of recipes bookmarked "
            + "by the authenticated user, ordered by save date (newest first). "
            + "Requires Firebase authentication token in Authorization header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Saved recipes retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PagedRecipeResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "Invalid pagination parameters (size < 1, size > 100, or malformed pageToken)",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing Firebase token",
                    content = @Content)
    })
    public ResponseEntity<PagedRecipeResponse> getSavedRecipes(
            @Parameter(description = "Cursor token from a previous response (omit for first page)")
            @RequestParam(name = "pageToken", required = false) String pageToken,
            @Parameter(description = "Page size (default: 20, min: 1, max: 100)")
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(hidden = true) @RequestAttribute("userId") String userId) {

        log.info("Fetching saved recipes for user {} (pageToken={}, size={})", userId, pageToken, size);
        PagedRecipeResponse response = recipeService.getSavedRecipes(userId, pageToken, size);
        return ResponseEntity.ok(response);
    }

    /**
     * Save (bookmark) a recipe for the authenticated user.
     * Idempotent – calling this when the recipe is already saved is a no-op.
     * Requires Firebase authentication.
     *
     * @param recipeId The recipe ID to save
     * @param userId   The authenticated user's Firebase UID (injected by auth filter)
     * @return 204 No Content on success
     */
    @PostMapping("/{recipeId}/save")
    @Operation(summary = "Save a recipe", description = "Bookmarks a recipe for the authenticated user. "
            + "Idempotent – calling this when the recipe is already saved is a no-op. "
            + "Requires Firebase authentication token in Authorization header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Recipe saved successfully", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing Firebase token",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Recipe not found", content = @Content),
            @ApiResponse(responseCode = "503", description = "Database not configured", content = @Content)
    })
    public ResponseEntity<Void> saveRecipeForUser(
            @Parameter(description = "Recipe ID to save", required = true) @PathVariable String recipeId,
            @Parameter(hidden = true) @RequestAttribute("userId") String userId) {

        MDC.put("recipe.id", recipeId);
        try {
            log.info("Saving recipe {} for user {}", recipeId, userId);
            recipeService.saveRecipeForUser(recipeId, userId);
            return ResponseEntity.noContent().build();
        } finally {
            MDC.remove("recipe.id");
        }
    }

    /**
     * Unsave (remove bookmark) a recipe for the authenticated user.
     * Idempotent – calling this when the recipe is not saved is a no-op.
     * Requires Firebase authentication.
     *
     * @param recipeId The recipe ID to unsave
     * @param userId   The authenticated user's Firebase UID (injected by auth filter)
     * @return 204 No Content on success
     */
    @DeleteMapping("/{recipeId}/save")
    @Operation(summary = "Unsave a recipe", description = "Removes a recipe from the authenticated user's bookmarks. "
            + "Idempotent – calling this when the recipe is not saved is a no-op. "
            + "Requires Firebase authentication token in Authorization header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Recipe unsaved successfully", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing Firebase token",
                    content = @Content),
            @ApiResponse(responseCode = "503", description = "Database not configured", content = @Content)
    })
    public ResponseEntity<Void> unsaveRecipeForUser(
            @Parameter(description = "Recipe ID to unsave", required = true) @PathVariable String recipeId,
            @Parameter(hidden = true) @RequestAttribute("userId") String userId) {

        MDC.put("recipe.id", recipeId);
        try {
            log.info("Unsaving recipe {} for user {}", recipeId, userId);
            recipeService.unsaveRecipeForUser(recipeId, userId);
            return ResponseEntity.noContent().build();
        } finally {
            MDC.remove("recipe.id");
        }
    }

    /**
     * Get a specific recipe by ID.
     * Requires Firebase authentication and user must own the recipe.
     *
     * @param recipeId The recipe ID
     * @param userId   The authenticated user's Firebase UID (injected by auth
     *                 filter)
     * @return The recipe if found and user has access
     */
    @GetMapping("/{recipeId}")
    @Operation(summary = "Get a specific recipe by ID", description = "Retrieves a recipe by its ID. User must own the recipe to access it. "
            + "Requires Firebase authentication token in Authorization header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Recipe retrieved successfully", content = @Content(schema = @Schema(implementation = RecipeResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing Firebase token", content = @Content),
            @ApiResponse(responseCode = "403", description = "Forbidden - user does not own this recipe", content = @Content),
            @ApiResponse(responseCode = "404", description = "Recipe not found", content = @Content)
    })
    public ResponseEntity<RecipeResponse> getRecipe(
            @Parameter(description = "Recipe ID", required = true) @PathVariable String recipeId,
            @Parameter(hidden = true) @RequestAttribute("userId") String userId) {

        MDC.put("recipe.id", recipeId);
        try {
            log.info("Fetching recipe {} for user {}", recipeId, userId);
            RecipeResponse recipe = recipeService.getRecipe(recipeId, userId);
            return ResponseEntity.ok(recipe);
        } finally {
            MDC.remove("recipe.id");
        }
    }

    /**
     * Update an existing recipe.
     * Requires Firebase authentication and user must own the recipe.
     *
     * @param recipeId The recipe ID to update
     * @param request  The recipe update details
     * @param userId   The authenticated user's Firebase UID (injected by auth
     *                 filter)
     * @return The updated recipe
     */
    @org.springframework.web.bind.annotation.PutMapping("/{recipeId}")
    @Operation(summary = "Update a recipe", description = "Updates an existing recipe. User must own the recipe to update it. "
            + "Requires Firebase authentication token in Authorization header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Recipe updated successfully", content = @Content(schema = @Schema(implementation = RecipeResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing Firebase token", content = @Content),
            @ApiResponse(responseCode = "403", description = "Forbidden - user does not own this recipe", content = @Content),
            @ApiResponse(responseCode = "404", description = "Recipe not found", content = @Content)
    })
    public ResponseEntity<RecipeResponse> updateRecipe(
            @Parameter(description = "Recipe ID to update", required = true) @PathVariable String recipeId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Updated recipe details", required = true, content = @Content(schema = @Schema(implementation = CreateRecipeRequest.class))) @Valid @RequestBody CreateRecipeRequest request,
            @Parameter(hidden = true) @RequestAttribute("userId") String userId) {

        MDC.put("recipe.id", recipeId);
        try {
            log.info("Updating recipe {} for user {}", recipeId, userId);
            RecipeResponse response = recipeService.updateRecipe(recipeId, request, userId);
            return ResponseEntity.ok(response);
        } finally {
            MDC.remove("recipe.id");
        }
    }

    /**
     * Delete a recipe by ID.
     * Requires Firebase authentication and user must own the recipe.
     *
     * @param recipeId The recipe ID to delete
     * @param userId   The authenticated user's Firebase UID (injected by auth
     *                 filter)
     * @return 204 No Content on successful deletion
     */
    @DeleteMapping("/{recipeId}")
    @Operation(summary = "Delete a recipe by ID", description = "Deletes a recipe by its ID. User must own the recipe to delete it. "
            + "Requires Firebase authentication token in Authorization header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Recipe deleted successfully", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing Firebase token", content = @Content),
            @ApiResponse(responseCode = "403", description = "Forbidden - user does not own this recipe", content = @Content),
            @ApiResponse(responseCode = "404", description = "Recipe not found", content = @Content)
    })
    public ResponseEntity<Void> deleteRecipe(
            @Parameter(description = "Recipe ID to delete", required = true) @PathVariable String recipeId,
            @Parameter(hidden = true) @RequestAttribute("userId") String userId) {

        MDC.put("recipe.id", recipeId);
        try {
            log.info("Deleting recipe {} for user {}", recipeId, userId);
            recipeService.deleteRecipe(recipeId, userId);
            return ResponseEntity.noContent().build();
        } finally {
            MDC.remove("recipe.id");
        }
    }

    /**
     * Update the sharing status of a recipe.
     * Requires Firebase authentication and user must own the recipe.
     *
     * @param recipeId The recipe ID to update
     * @param isPublic The new sharing status
     * @param userId   The authenticated user's Firebase UID (injected by auth filter)
     * @return The updated recipe
     */
    @PatchMapping("/{recipeId}/sharing")
    @Operation(summary = "Update recipe sharing status", description = "Updates whether a recipe is public or private. User must own the recipe. "
            + "Requires Firebase authentication token in Authorization header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Sharing status updated successfully", content = @Content(schema = @Schema(implementation = RecipeResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing Firebase token", content = @Content),
            @ApiResponse(responseCode = "403", description = "Forbidden - user does not own this recipe", content = @Content),
            @ApiResponse(responseCode = "404", description = "Recipe not found", content = @Content)
    })
    public ResponseEntity<RecipeResponse> updateRecipeSharing(
            @Parameter(description = "Recipe ID to update", required = true) @PathVariable String recipeId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "New sharing status", required = true) @Valid @RequestBody UpdateSharingRequest request,
            @Parameter(hidden = true) @RequestAttribute("userId") String userId) {

        MDC.put("recipe.id", recipeId);
        try {
            log.info("Updating sharing status for recipe {} to {} for user {}", recipeId, request.getIsPublic(), userId);
            RecipeResponse response = recipeService.updateRecipeSharing(recipeId, request.getIsPublic(), userId);
            return ResponseEntity.ok(response);
        } finally {
            MDC.remove("recipe.id");
        }
    }

    /**
     * Like a recipe for the authenticated user.
     * Idempotent – calling this when the recipe is already liked is a no-op.
     * Requires Firebase authentication.
     *
     * @param recipeId The recipe ID to like
     * @param userId   The authenticated user's Firebase UID (injected by auth filter)
     * @return 204 No Content on success
     */
    @PostMapping("/{recipeId}/like")
    @Operation(summary = "Like a recipe", description = "Likes a recipe for the authenticated user. "
            + "Idempotent – calling this when the recipe is already liked is a no-op. "
            + "Requires Firebase authentication token in Authorization header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Recipe liked successfully", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing Firebase token",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Recipe not found", content = @Content),
            @ApiResponse(responseCode = "503", description = "Database not configured", content = @Content)
    })
    public ResponseEntity<Void> likeRecipe(
            @Parameter(description = "Recipe ID to like", required = true) @PathVariable String recipeId,
            @Parameter(hidden = true) @RequestAttribute("userId") String userId) {

        MDC.put("recipe.id", recipeId);
        try {
            log.info("Liking recipe {} for user {}", recipeId, userId);
            recipeService.likeRecipe(recipeId, userId);
            return ResponseEntity.noContent().build();
        } finally {
            MDC.remove("recipe.id");
        }
    }

    /**
     * Unlike a recipe for the authenticated user.
     * Idempotent – calling this when the recipe is not liked is a no-op.
     * Requires Firebase authentication.
     *
     * @param recipeId The recipe ID to unlike
     * @param userId   The authenticated user's Firebase UID (injected by auth filter)
     * @return 204 No Content on success
     */
    @DeleteMapping("/{recipeId}/like")
    @Operation(summary = "Unlike a recipe", description = "Removes a like from a recipe for the authenticated user. "
            + "Idempotent – calling this when the recipe is not liked is a no-op. "
            + "Requires Firebase authentication token in Authorization header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Recipe unliked successfully", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing Firebase token",
                    content = @Content),
            @ApiResponse(responseCode = "503", description = "Database not configured", content = @Content)
    })
    public ResponseEntity<Void> unlikeRecipe(
            @Parameter(description = "Recipe ID to unlike", required = true) @PathVariable String recipeId,
            @Parameter(hidden = true) @RequestAttribute("userId") String userId) {

        MDC.put("recipe.id", recipeId);
        try {
            log.info("Unliking recipe {} for user {}", recipeId, userId);
            recipeService.unlikeRecipe(recipeId, userId);
            return ResponseEntity.noContent().build();
        } finally {
            MDC.remove("recipe.id");
        }
    }
}
