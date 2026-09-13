package com.recipe.storage.mapper;

import com.recipe.shared.model.NutritionValues;
import com.recipe.shared.model.NutritionalInfo;
import com.recipe.shared.model.Recipe;
import com.recipe.shared.model.RecipeTips;
import com.recipe.storage.dto.RecipeResponse;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Mapper component to convert between Recipe domain models, requests, and DTOs.
 */
@Slf4j
@Component
public class RecipeMapper {

  /**
   * Map Recipe domain model to RecipeResponse DTO.
   *
   * @param recipe the domain recipe
   * @return the RecipeResponse DTO or null if input is null
   */
  public RecipeResponse mapToResponse(Recipe recipe) {
    if (recipe == null) {
      return null;
    }

    Map<String, Object> nutritionMap = null;
    if (recipe.getNutritionalInfo() != null
        && recipe.getNutritionalInfo().getPerServing() != null) {
      nutritionMap = recipe.getNutritionalInfo().getPerServing().toMap();
    }

    Map<String, List<String>> tipsMap = null;
    if (recipe.getTips() != null) {
      try {
        tipsMap = recipe.getTips().toMap();
      } catch (Exception e) {
        log.warn("Failed to map tips", e);
        tipsMap = null;
      }
    }

    return RecipeResponse.builder()
        .id(recipe.getId())
        .userId(recipe.getUserId())
        .title(recipe.getRecipeName())
        .description(recipe.getDescription())
        .ingredients(recipe.getIngredients())
        .instructions(recipe.getInstructions())
        .prepTime(recipe.getPrepTimeMinutes())
        .cookTime(recipe.getCookTimeMinutes())
        .totalTime(recipe.getCalculatedTotalTimeMinutes())
        .servings(recipe.getServings())
        .nutrition(nutritionMap)
        .tips(tipsMap)
        .imageUrl(recipe.getImageUrl())
        .source(recipe.getSource())
        .createdAt(recipe.getCreatedAt())
        .updatedAt(recipe.getUpdatedAt())
        .tags(recipe.getTags())
        .dietaryRestrictions(recipe.getDietaryRestrictions())
        .isPublic(recipe.isPublicRecipe())
        .build();
  }

  /**
   * Helper to map nutrition map to NutritionalInfo.
   *
   * @param nutritionMap map of nutritional values
   * @return NutritionalInfo or null
   */
  public NutritionalInfo mapToNutritionalInfo(Map<String, Object> nutritionMap) {
    if (nutritionMap == null) {
      return null;
    }
    return NutritionalInfo.builder()
        .perServing(NutritionValues.fromMap(nutritionMap))
        .build();
  }

  /**
   * Helper to map tips map to RecipeTips.
   *
   * @param tipsMap map of tips
   * @return RecipeTips or null
   */
  public RecipeTips mapToRecipeTips(Map<String, List<String>> tipsMap) {
    if (tipsMap == null) {
      return null;
    }
    return RecipeTips.fromMap(tipsMap);
  }
}
