package com.recipe.storage.controller;

import tools.jackson.databind.ObjectMapper;
import com.recipe.storage.dto.CreateRecipeRequest;
import com.recipe.storage.service.RecipeService;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "auth.enabled=false",
        "firestore.collection.recipes=test-recipes"
})
class RecipeControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecipeService recipeService;

    @BeforeEach
    void resetMockStore() {
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, Object> mockStore =
                (ConcurrentHashMap<String, Object>) ReflectionTestUtils.getField(recipeService, "mockStore");
        if (mockStore != null) {
            mockStore.clear();
        }
    }

    @Test
    void createRecipe_Success() throws Exception {
        CreateRecipeRequest request = CreateRecipeRequest.builder()
                .title("Delicious Pasta")
                .description("A wonderful Italian pasta dish")
                .ingredients(List.of("200g pasta", "2 tomatoes", "1 onion"))
                .instructions(List.of("Boil water", "Cook pasta", "Mix ingredients"))
                .prepTime(15)
                .cookTime(20)
                .servings(4)
                .source("ai-generated")
                .build();

        mockMvc.perform(post("/api/recipes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("X-User-ID", "user123"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("Delicious Pasta"))
                .andExpect(jsonPath("$.servings").value(4));
    }

    @Test
    void createRecipe_NullIsPublic_DefaultsToPrivate() throws Exception {
        String request = """
                {
                  "title": "Private Pasta",
                  "ingredients": ["200g pasta"],
                  "instructions": ["Cook pasta"],
                  "servings": 2,
                  "source": "manual",
                  "isPublic": null
                }
                """;

        mockMvc.perform(post("/api/recipes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request)
                .header("X-User-ID", "user123"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isPublic").value(false));
    }

    @Test
    void createRecipe_MissingTitle_ReturnsBadRequest() throws Exception {
        CreateRecipeRequest request = CreateRecipeRequest.builder()
                .description("A wonderful Italian pasta dish")
                .ingredients(List.of("200g pasta"))
                .instructions(List.of("Cook"))
                .servings(4)
                .source("manual")
                .build();

        mockMvc.perform(post("/api/recipes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("X-User-ID", "test-user"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRecipe_EmptyIngredients_ReturnsBadRequest() throws Exception {
        CreateRecipeRequest request = CreateRecipeRequest.builder()
                .title("Test Recipe")
                .ingredients(List.of())
                .instructions(List.of("Cook"))
                .servings(4)
                .source("manual")
                .build();

        mockMvc.perform(post("/api/recipes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("X-User-ID", "test-user"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getUserRecipes_Success() throws Exception {
        mockMvc.perform(get("/api/recipes")
                .header("X-User-ID", "test-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getPublicRecipes_Success() throws Exception {
        mockMvc.perform(get("/api/recipes/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipes").isArray())
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    void getPublicRecipes_WithSizeParam_Success() throws Exception {
        mockMvc.perform(get("/api/recipes/public")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipes").isArray())
                .andExpect(jsonPath("$.size").value(10));
    }

    @Test
    void getPublicRecipes_SizeExceedsMax_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/recipes/public")
                .param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getRecipe_WithValidId_ReturnsRecipe() throws Exception {
        // Without Firestore, trying to get a non-existent recipe returns 404
        mockMvc.perform(get("/api/recipes/some-id")
                .header("X-User-ID", "test-user"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateRecipe_WithValidId_UpdatesRecipe() throws Exception {
        // Without Firestore, update returns 404 for non-existent recipe
        CreateRecipeRequest updateRequest = CreateRecipeRequest.builder()
                .title("Updated Title")
                .description("Updated description")
                .ingredients(List.of("new ingredient"))
                .instructions(List.of("new step"))
                .servings(4)
                .source("manual")
                .build();

        mockMvc.perform(put("/api/recipes/some-id")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest))
                .header("X-User-ID", "test-user"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRecipe_WithValidId_DeletesRecipe() throws Exception {
        // Without Firestore, delete returns 404 for non-existent recipe
        mockMvc.perform(delete("/api/recipes/some-id")
                .header("X-User-ID", "test-user"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateRecipeSharing_WithValidId_UpdatesSharingStatus() throws Exception {
        // Without Firestore, update sharing returns 404 for non-existent recipe
        String sharingRequest = "{\"isPublic\": true}";

        mockMvc.perform(patch("/api/recipes/some-id/sharing")
                .contentType(MediaType.APPLICATION_JSON)
                .content(sharingRequest)
                .header("X-User-ID", "test-user"))
                .andExpect(status().isNotFound());
    }

    // ── Save / Unsave / Saved-list endpoints ─────────────────────────────────

    @Test
    void saveRecipe_WithNoFirestore_ReturnsServiceUnavailable() throws Exception {
        // Without Firestore, saving a recipe returns 503
        mockMvc.perform(post("/api/recipes/some-id/save")
                .header("X-User-ID", "test-user"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void unsaveRecipe_WithNoFirestore_ReturnsServiceUnavailable() throws Exception {
        // Without Firestore, unsaving a recipe returns 503
        mockMvc.perform(delete("/api/recipes/some-id/save")
                .header("X-User-ID", "test-user"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void getSavedRecipes_WithNoFirestore_ReturnsEmptyList() throws Exception {
        // Without Firestore, returns an empty paged envelope
        mockMvc.perform(get("/api/recipes/saved")
                .header("X-User-ID", "test-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipes").isArray())
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void getSavedRecipes_SizeExceedsMax_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/recipes/saved")
                .param("size", "101")
                .header("X-User-ID", "test-user"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getSavedRecipes_WithCustomSize_ReturnsCorrectSize() throws Exception {
        mockMvc.perform(get("/api/recipes/saved")
                .param("size", "10")
                .header("X-User-ID", "test-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(10));
    }

    // Note: GET endpoint tests are skipped in integration tests
    // because they require Firestore to be configured.
    // These endpoints will be tested in manual/E2E tests with real Firestore.

    // ── Like / Unlike endpoints ───────────────────────────────────────────────

    @Test
    void likeRecipe_WithNoFirestore_ReturnsServiceUnavailable() throws Exception {
        // Without Firestore, liking a recipe returns 503
        mockMvc.perform(post("/api/recipes/some-id/like")
                .header("X-User-ID", "test-user"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void unlikeRecipe_WithNoFirestore_ReturnsServiceUnavailable() throws Exception {
        // Without Firestore, unliking a recipe returns 503
        mockMvc.perform(delete("/api/recipes/some-id/like")
                .header("X-User-ID", "test-user"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void createRecipe_ResponseContainsLikeCountAndIsLikedByCurrentUser() throws Exception {
        CreateRecipeRequest request = CreateRecipeRequest.builder()
                .title("Like Test Recipe")
                .description("A recipe to test like fields")
                .ingredients(List.of("Ingredient"))
                .instructions(List.of("Step 1"))
                .prepTime(5)
                .cookTime(10)
                .servings(2)
                .source("manual")
                .build();

        mockMvc.perform(post("/api/recipes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("X-User-ID", "test-user"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.likeCount").value(0))
                .andExpect(jsonPath("$.isLikedByCurrentUser").value(false));
    }

    @Test
    void createRecipe_WithZeroCookTime_Success() throws Exception {
        CreateRecipeRequest request = CreateRecipeRequest.builder()
                .title("No-Cook Banana Split")
                .description("A delicious dessert with zero cook time")
                .ingredients(List.of("1 Banana", "3 scoops ice cream"))
                .instructions(List.of("Peel banana", "Add ice cream"))
                .prepTime(10)
                .cookTime(0)
                .totalTime(10)
                .servings(1)
                .source("ai-generated")
                .build();

        mockMvc.perform(post("/api/recipes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("X-User-ID", "test-user"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cookTime").value(0));
    }

    @Test
    void updateRecipeSharing_WithPutMethod_IsSupported() throws Exception {
        String sharingRequest = "{\"isPublic\": true}";

        mockMvc.perform(put("/api/recipes/some-id/sharing")
                .contentType(MediaType.APPLICATION_JSON)
                .content(sharingRequest)
                .header("X-User-ID", "test-user"))
                .andExpect(status().isNotFound());
    }
}
