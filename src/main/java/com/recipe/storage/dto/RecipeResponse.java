package com.recipe.storage.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for recipe data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Recipe response with all recipe details including metadata")
public class RecipeResponse {

    @Schema(description = "Unique recipe identifier (Firestore document ID)", example = "abc123def456")
    private String id;

    @Schema(description = "Firebase user ID of the recipe owner", example = "firebase-uid-123")
    private String userId;

    @Schema(description = "Recipe title", example = "Spaghetti Carbonara")
    private String title;

    @Schema(description = "Recipe description", example = "A classic Italian pasta dish with eggs, cheese, and pancetta")
    private String description;

    @Schema(description = "List of ingredients with quantities", example = "[\"400g spaghetti\", \"200g pancetta\", \"4 large eggs\"]")
    private List<String> ingredients;

    @Schema(description = "Step-by-step cooking instructions", example = "[\"Boil pasta in salted water\", \"Fry pancetta until crispy\"]")
    private List<String> instructions;

    @Schema(description = "Preparation time in minutes", example = "15")
    private Integer prepTime;

    @Schema(description = "Cooking time in minutes", example = "20")
    private Integer cookTime;

    @Schema(description = "Number of servings", example = "4")
    private Integer servings;

    @Schema(description = "Nutritional information per serving", example = "{\"calories\": 450, \"protein\": 20, \"carbs\": 55, \"fat\": 18}")
    private Map<String, Object> nutrition;

    @Schema(description = "Cooking tips organized by category", example = "{\"prep\": [\"Use room temperature eggs\"], "
            + "\"serving\": [\"Serve immediately\"]}")
    private Map<String, List<String>> tips;

    @Schema(description = "URL to recipe image", example = "https://example.com/images/carbonara.jpg")
    private String imageUrl;

    @Schema(description = "Source of the recipe", example = "ai-generated", allowableValues = { "ai-generated",
            "manual" })
    private String source;

    @Schema(description = "Recipe creation timestamp", example = "2024-01-15T10:30:00Z")
    private Instant createdAt;

    @Schema(description = "Recipe last update timestamp", example = "2024-01-15T14:30:00Z")
    private Instant updatedAt;

    @Schema(description = "Recipe tags for categorization", example = "[\"Italian\", \"Pasta\", \"Quick\"]")
    private List<String> tags;

    @Schema(description = "Dietary restrictions/labels", example = "[\"Gluten-Free\", \"Vegetarian\"]")
    private List<String> dietaryRestrictions;

    @Schema(description = "Whether the recipe is shared with everyone", example = "true")
    @JsonProperty("isPublic")
    private boolean isPublic;

    @Schema(description = "Display name of the recipe author (populated for public recipes)", nullable = true,
            example = "Jane Smith")
    private String authorDisplayName;

    @Schema(description = "Whether this recipe is saved/bookmarked by the current authenticated user",
            example = "false")
    @JsonProperty("isSavedByCurrentUser")
    private boolean isSavedByCurrentUser;

    @Schema(description = "Number of likes this recipe has received", example = "42")
    private int likeCount;

    @Schema(description = "Whether this recipe is liked by the current authenticated user",
            example = "false")
    @JsonProperty("isLikedByCurrentUser")
    private boolean isLikedByCurrentUser;
}
