package com.recipe.storage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new recipe.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request body for creating a new recipe")
public class CreateRecipeRequest {

    @NotBlank(message = "Title is required")
    @Schema(description = "Recipe title", example = "Spaghetti Carbonara", required = true)
    private String title;

    @Schema(description = "Recipe description", example = "A classic Italian pasta dish with eggs, cheese, and pancetta")
    private String description;

    @NotEmpty(message = "At least one ingredient is required")
    @Schema(description = "List of ingredients with quantities", example = "[\"400g spaghetti\", \"200g pancetta\", \"4 large eggs\", "
            + "\"100g Parmesan cheese\"]", required = true)
    private List<String> ingredients;

    @NotEmpty(message = "At least one instruction is required")
    @Schema(description = "Step-by-step cooking instructions", example = "[\"Boil pasta in salted water\", \"Fry pancetta until crispy\", "
            + "\"Mix eggs and cheese\"]", required = true)
    private List<String> instructions;

    @PositiveOrZero(message = "Prep time must be positive or zero")
    @Schema(description = "Preparation time in minutes", example = "15")
    private Integer prepTime;

    @PositiveOrZero(message = "Cook time must be positive or zero")
    @Schema(description = "Cooking time in minutes", example = "20")
    private Integer cookTime;

    @PositiveOrZero(message = "Total time must be positive or zero")
    @Schema(description = "Total time in minutes (optional; computed as prepTime + cookTime if omitted)", example = "35")
    private Integer totalTime;

    @NotNull(message = "Servings is required")
    @Positive(message = "Servings must be positive")
    @Schema(description = "Number of servings", example = "4", required = true)
    private Integer servings;

    @Schema(description = "Nutritional information per serving", example = "{\"calories\": 450, \"protein\": 20, \"carbs\": 55, \"fat\": 18}")
    private Map<String, Object> nutrition;

    @Schema(description = "Cooking tips organized by category", example = "{\"prep\": [\"Use room temperature eggs\"], "
            + "\"serving\": [\"Serve immediately\"]}")
    private Map<String, List<String>> tips;

    @Schema(description = "URL to recipe image", example = "https://example.com/images/carbonara.jpg")
    private String imageUrl;

    @NotBlank(message = "Source is required")
    @Schema(description = "Source of the recipe", example = "ai-generated", allowableValues = { "ai-generated",
            "manual" }, required = true)
    private String source;

    @Schema(description = "Recipe tags for categorization", example = "[\"Italian\", \"Pasta\", \"Quick\"]")
    private List<String> tags;

    @Schema(description = "Dietary restrictions/labels", example = "[\"Gluten-Free\", \"Vegetarian\"]")
    private List<String> dietaryRestrictions;

    @Schema(description = "Whether the recipe is shared with everyone", example = "true", defaultValue = "false")
    private Boolean isPublic;

    @com.fasterxml.jackson.annotation.JsonIgnore
    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }
}
