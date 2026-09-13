package com.recipe.storage.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.recipe.shared.model.NutritionalInfo;
import com.recipe.shared.model.NutritionValues;
import com.recipe.shared.model.Recipe;
import com.recipe.storage.dto.RecipeResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RecipeMapperTest {

  private final RecipeMapper mapper = new RecipeMapper();

  @Test
  void mapToResponse_NullRecipe_ReturnsNull() {
    assertNull(mapper.mapToResponse(null));
  }

  @Test
  void mapToResponse_ValidRecipe_MapsCorrectly() {
    NutritionalInfo nutrition = NutritionalInfo.builder()
        .perServing(NutritionValues.builder().calories(500.0).protein(25.0).build())
        .build();

    Recipe recipe = Recipe.builder()
        .id("rec-123")
        .userId("usr-456")
        .recipeName("Pasta")
        .description("Delicious")
        .ingredients(List.of("Noodles", "Sauce"))
        .instructions(List.of("Boil", "Serve"))
        .prepTimeMinutes(10)
        .cookTimeMinutes(15)
        .totalTimeMinutes(25)
        .servings(2)
        .nutritionalInfo(nutrition)
        .imageUrl("https://example.com/pasta.jpg")
        .source("manual")
        .createdAt(Instant.parse("2026-01-01T10:00:00Z"))
        .updatedAt(Instant.parse("2026-01-01T12:00:00Z"))
        .tags(List.of("Italian", "Quick"))
        .dietaryRestrictions(List.of("Vegetarian"))
        .publicRecipe(true)
        .build();

    RecipeResponse response = mapper.mapToResponse(recipe);

    assertNotNull(response);
    assertEquals("rec-123", response.getId());
    assertEquals("usr-456", response.getUserId());
    assertEquals("Pasta", response.getTitle());
    assertEquals("Delicious", response.getDescription());
    assertEquals(List.of("Noodles", "Sauce"), response.getIngredients());
    assertEquals(10, response.getPrepTime());
    assertEquals(15, response.getCookTime());
    assertEquals(25, response.getTotalTime());
    assertEquals(2, response.getServings());
    assertTrue(response.isPublic());
    assertNotNull(response.getNutrition());
    assertEquals(500.0, response.getNutrition().get("calories"));
  }

  @Test
  void mapToNutritionalInfo_NullMap_ReturnsNull() {
    assertNull(mapper.mapToNutritionalInfo(null));
  }

  @Test
  void mapToNutritionalInfo_ValidMap_ReturnsNutritionalInfo() {
    Map<String, Object> map = Map.of("calories", 350.0, "protein", 15.0);
    NutritionalInfo info = mapper.mapToNutritionalInfo(map);

    assertNotNull(info);
    assertNotNull(info.getPerServing());
    assertEquals(350.0, info.getPerServing().getCalories());
    assertEquals(15.0, info.getPerServing().getProtein());
  }
}
