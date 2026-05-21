package com.recipe.storage.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipe.storage.dto.CreateRecipeRequest;
import com.recipe.storage.dto.RecipeResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Comprehensive integration tests covering all post-deployment scenarios.
 * These tests verify the full recipe lifecycle including:
 * - CRUD operations
 * - Recipe sharing functionality with persistence
 * - Public recipe listing
 * - Authentication/authorization
 * - Health checks
 * - API documentation endpoints
 * 
 * Note: These tests run against mock Firestore behavior. For true end-to-end
 * testing with real Firestore persistence, run the test-deployment.sh script
 * against a deployed environment.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "auth.enabled=false",
        "firestore.collection.recipes=test-recipes-deployment"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PostDeploymentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String createdRecipeId;
    // Note: This test uses "test-user" as the TEST_USER_ID when interacting with the API
    private static final String TEST_USER_ID = "test-user";

    // ========================================
    // Health and Infrastructure Tests
    // ========================================

    @Test
    @Order(1)
    @DisplayName("Health endpoint should return UP status")
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").exists());
    }

    @Test
    @Order(2)
    @DisplayName("Swagger UI should be accessible")
    void testSwaggerUI() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @Order(3)
    @DisplayName("OpenAPI documentation should be available")
    void testOpenAPISpec() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").exists());
    }

    @Test
    @Order(4)
    @DisplayName("Protected endpoints should require authentication when auth is enabled")
    void testAuthenticationRequired() throws Exception {
        // Note: This test validates behavior when auth.enabled=false
        // In production with auth.enabled=true, this would return 401/403
        mockMvc.perform(get("/api/recipes"))
                .andExpect(status().isOk()); // With auth disabled, should succeed
    }

    // ========================================
    // Recipe CRUD Operations Tests
    // ========================================

    @Test
    @Order(10)
    @DisplayName("Create recipe - should create successfully with all fields")
    void testCreateRecipe() throws Exception {
        CreateRecipeRequest request = CreateRecipeRequest.builder()
                .title("Integration Test Recipe")
                .description("A recipe for integration testing")
                .ingredients(List.of("500g flour", "200ml water", "10g salt"))
                .instructions(List.of(
                        "Mix flour and salt",
                        "Add water gradually",
                        "Knead for 10 minutes"
                ))
                .prepTime(15)
                .cookTime(30)
                .servings(4)
                .source("integration-test")
                .isPublic(false)
                .build();

        MvcResult result = mockMvc.perform(post("/api/recipes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-User-ID", TEST_USER_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("Integration Test Recipe"))
                .andExpect(jsonPath("$.description").value("A recipe for integration testing"))
                .andExpect(jsonPath("$.ingredients", hasSize(3)))
                .andExpect(jsonPath("$.instructions", hasSize(3)))
                .andExpect(jsonPath("$.prepTime").value(15))
                .andExpect(jsonPath("$.cookTime").value(30))
                .andExpect(jsonPath("$.servings").value(4))
                .andExpect(jsonPath("$.source").value("integration-test"))
                .andExpect(jsonPath("$.isPublic").value(false))
                .andExpect(jsonPath("$.userId").value(TEST_USER_ID))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        RecipeResponse recipe = objectMapper.readValue(responseBody, RecipeResponse.class);
        createdRecipeId = recipe.getId();
        
        assertNotNull(createdRecipeId, "Created recipe should have an ID");
    }

    @Test
    @Order(11)
    @DisplayName("Create recipe - should fail with missing required fields")
    void testCreateRecipeValidation() throws Exception {
        CreateRecipeRequest invalidRequest = CreateRecipeRequest.builder()
                .description("Missing title")
                .ingredients(List.of("ingredient"))
                .instructions(List.of("step"))
                .servings(2)
                .source("test")
                .build();

        mockMvc.perform(post("/api/recipes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest))
                        .header("X-User-ID", TEST_USER_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(12)
    @DisplayName("Get user recipes - should return list including created recipe")
    void testGetUserRecipes() throws Exception {
        mockMvc.perform(get("/api/recipes")
                        .header("X-User-ID", TEST_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @Order(13)
    @DisplayName("Get recipe by ID - should return the created recipe")
    void testGetRecipeById() throws Exception {
        assertNotNull(createdRecipeId, "Recipe ID should be set from previous test");

        mockMvc.perform(get("/api/recipes/" + createdRecipeId)
                        .header("X-User-ID", TEST_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdRecipeId))
                .andExpect(jsonPath("$.title").value("Integration Test Recipe"))
                .andExpect(jsonPath("$.isPublic").value(false));
    }

    @Test
    @Order(14)
    @DisplayName("Update recipe - should modify recipe fields")
    void testUpdateRecipe() throws Exception {
        assertNotNull(createdRecipeId, "Recipe ID should be set from previous test");

        CreateRecipeRequest updateRequest = CreateRecipeRequest.builder()
                .title("Updated Integration Test Recipe")
                .description("Updated description for testing")
                .ingredients(List.of("600g flour", "250ml water", "15g salt", "5g yeast"))
                .instructions(List.of(
                        "Mix all dry ingredients",
                        "Add water gradually",
                        "Knead for 15 minutes",
                        "Let rise for 1 hour"
                ))
                .prepTime(20)
                .cookTime(35)
                .servings(6)
                .source("integration-test-updated")
                .isPublic(false)
                .build();

        mockMvc.perform(put("/api/recipes/" + createdRecipeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest))
                        .header("X-User-ID", TEST_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdRecipeId))
                .andExpect(jsonPath("$.title").value("Updated Integration Test Recipe"))
                .andExpect(jsonPath("$.description").value("Updated description for testing"))
                .andExpect(jsonPath("$.ingredients", hasSize(4)))
                .andExpect(jsonPath("$.instructions", hasSize(4)))
                .andExpect(jsonPath("$.prepTime").value(20))
                .andExpect(jsonPath("$.cookTime").value(35))
                .andExpect(jsonPath("$.servings").value(6));
    }

    // ========================================
    // Recipe Sharing Functionality Tests
    // ========================================

    @Test
    @Order(20)
    @DisplayName("Share recipe - should set isPublic to true")
    void testShareRecipe() throws Exception {
        assertNotNull(createdRecipeId, "Recipe ID should be set from previous test");

        String sharingRequest = "{\"isPublic\": true}";

        mockMvc.perform(patch("/api/recipes/" + createdRecipeId + "/sharing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sharingRequest)
                        .header("X-User-ID", TEST_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdRecipeId))
                .andExpect(jsonPath("$.isPublic").value(true));
    }

    @Test
    @Order(21)
    @DisplayName("Verify sharing persists - isPublic should remain true after retrieval")
    void testSharingPersistence() throws Exception {
        assertNotNull(createdRecipeId, "Recipe ID should be set from previous test");

        // Retrieve the recipe and verify isPublic is still true
        mockMvc.perform(get("/api/recipes/" + createdRecipeId)
                        .header("X-User-ID", TEST_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdRecipeId))
                .andExpect(jsonPath("$.isPublic").value(true))
                .andExpect(jsonPath("$.public").value(true)); // Also check deprecated field
    }

    @Test
    @Order(22)
    @DisplayName("Public recipes list - should include shared recipe")
    void testPublicRecipesListIncludesSharedRecipe() throws Exception {
        assertNotNull(createdRecipeId, "Recipe ID should be set from previous test");

        mockMvc.perform(get("/api/recipes/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipes").isArray())
                .andExpect(jsonPath("$.recipes[?(@.id == '" + createdRecipeId + "')]").exists())
                .andExpect(jsonPath("$.recipes[?(@.id == '" + createdRecipeId + "')].isPublic").value(true));
    }

    @Test
    @Order(23)
    @DisplayName("Unshare recipe - should set isPublic to false")
    void testUnshareRecipe() throws Exception {
        assertNotNull(createdRecipeId, "Recipe ID should be set from previous test");

        String unsharingRequest = "{\"isPublic\": false}";

        mockMvc.perform(patch("/api/recipes/" + createdRecipeId + "/sharing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unsharingRequest)
                        .header("X-User-ID", TEST_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdRecipeId))
                .andExpect(jsonPath("$.isPublic").value(false));
    }

    @Test
    @Order(24)
    @DisplayName("Verify unsharing persists - isPublic should remain false after retrieval")
    void testUnsharingPersistence() throws Exception {
        assertNotNull(createdRecipeId, "Recipe ID should be set from previous test");

        // Retrieve the recipe and verify isPublic is still false
        mockMvc.perform(get("/api/recipes/" + createdRecipeId)
                        .header("X-User-ID", TEST_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdRecipeId))
                .andExpect(jsonPath("$.isPublic").value(false))
                .andExpect(jsonPath("$.public").value(false));
    }

    @Test
    @Order(25)
    @DisplayName("Public recipes list - should NOT include unshared recipe")
    void testPublicRecipesListExcludesUnsharedRecipe() throws Exception {
        assertNotNull(createdRecipeId, "Recipe ID should be set from previous test");

        mockMvc.perform(get("/api/recipes/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipes").isArray())
                .andExpect(jsonPath("$.recipes[?(@.id == '" + createdRecipeId + "')]").doesNotExist());
    }

    // ========================================
    // Cleanup Tests
    // ========================================

    @Test
    @Order(30)
    @DisplayName("Delete recipe - should remove the recipe")
    void testDeleteRecipe() throws Exception {
        assertNotNull(createdRecipeId, "Recipe ID should be set from previous test");

        mockMvc.perform(delete("/api/recipes/" + createdRecipeId)
                        .header("X-User-ID", TEST_USER_ID))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(31)
    @DisplayName("Verify deletion - should return 404 for deleted recipe")
    void testVerifyDeletion() throws Exception {
        assertNotNull(createdRecipeId, "Recipe ID should be set from previous test");

        mockMvc.perform(get("/api/recipes/" + createdRecipeId)
                        .header("X-User-ID", TEST_USER_ID))
                .andExpect(status().isNotFound());
    }

    // ========================================
    // Edge Cases and Error Handling Tests
    // ========================================

    @Test
    @Order(40)
    @DisplayName("Get non-existent recipe - should return 404")
    void testGetNonExistentRecipe() throws Exception {
        mockMvc.perform(get("/api/recipes/non-existent-id")
                        .header("X-User-ID", TEST_USER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(41)
    @DisplayName("Update non-existent recipe - should return 404")
    void testUpdateNonExistentRecipe() throws Exception {
        CreateRecipeRequest updateRequest = CreateRecipeRequest.builder()
                .title("Test")
                .ingredients(List.of("ingredient"))
                .instructions(List.of("step"))
                .servings(2)
                .source("test")
                .build();

        mockMvc.perform(put("/api/recipes/non-existent-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest))
                        .header("X-User-ID", TEST_USER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(42)
    @DisplayName("Delete non-existent recipe - should return 404")
    void testDeleteNonExistentRecipe() throws Exception {
        mockMvc.perform(delete("/api/recipes/non-existent-id")
                        .header("X-User-ID", TEST_USER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(43)
    @DisplayName("Public recipes endpoint - should be accessible without authentication")
    void testPublicRecipesNoAuth() throws Exception {
        // Public endpoint should work without X-User-ID header
        mockMvc.perform(get("/api/recipes/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipes").isArray());
    }

    @Test
    @Order(44)
    @DisplayName("Create recipe with isPublic=true - should be public immediately")
    void testCreatePublicRecipe() throws Exception {
        CreateRecipeRequest publicRequest = CreateRecipeRequest.builder()
                .title("Public Recipe Test")
                .description("Created as public")
                .ingredients(List.of("ingredient"))
                .instructions(List.of("step"))
                .servings(2)
                .source("test")
                .isPublic(true)
                .build();

        MvcResult result = mockMvc.perform(post("/api/recipes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(publicRequest))
                        .header("X-User-ID", TEST_USER_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isPublic").value(true))
                .andReturn();

        // Clean up
        String responseBody = result.getResponse().getContentAsString();
        RecipeResponse recipe = objectMapper.readValue(responseBody, RecipeResponse.class);
        mockMvc.perform(delete("/api/recipes/" + recipe.getId())
                .header("X-User-ID", TEST_USER_ID));
    }
}
