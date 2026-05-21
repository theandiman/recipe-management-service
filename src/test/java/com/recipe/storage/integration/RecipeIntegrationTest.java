package com.recipe.storage.integration;

import com.recipe.storage.dto.CreateRecipeRequest;
import com.recipe.storage.dto.PagedRecipeResponse;
import com.recipe.storage.dto.RecipeResponse;
import com.recipe.storage.dto.UpdateSharingRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive integration tests for Recipe Storage Service.
 * These tests verify all CRUD operations and business logic end-to-end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RecipeIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private static String recipeId;
    private static final String TEST_USER_ID = "test-user-123";
    private static final String OTHER_USER_ID = "other-user-456";

    private String getBaseUrl() {
        return "http://localhost:" + port + "/api/recipes";
    }

    private HttpHeaders getAuthHeaders(String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("userId", userId); // Mock auth header - in production this is set by auth filter
        return headers;
    }

    private CreateRecipeRequest createTestRecipe(String title, boolean isPublic) {
        CreateRecipeRequest request = new CreateRecipeRequest();
        request.setTitle(title);
        request.setDescription("Integration test recipe");
        request.setIngredients(Arrays.asList("Ingredient 1", "Ingredient 2", "Ingredient 3"));
        request.setInstructions(Arrays.asList("Step 1", "Step 2", "Step 3"));
        request.setPrepTime(15);
        request.setCookTime(30);
        request.setServings(4);
        request.setSource("integration-test");
        request.setPublic(isPublic);
        return request;
    }

    @Test
    @Order(1)
    @DisplayName("Health endpoint should be accessible without authentication")
    void testHealthEndpoint() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/actuator/health",
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    @Order(2)
    @DisplayName("Create recipe - should return 201 and recipe with ID")
    void testCreateRecipe() {
        CreateRecipeRequest request = createTestRecipe("Test Recipe 1", false);
        HttpEntity<CreateRecipeRequest> entity = new HttpEntity<>(request, getAuthHeaders(TEST_USER_ID));

        ResponseEntity<RecipeResponse> response = restTemplate.postForEntity(
                getBaseUrl(),
                entity,
                RecipeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Test Recipe 1");
        assertThat(response.getBody().getUserId()).isEqualTo(TEST_USER_ID);
        assertThat(response.getBody().isPublic()).isFalse();

        recipeId = response.getBody().getId();
    }

    @Test
    @Order(3)
    @DisplayName("Get recipe by ID - should return recipe for owner")
    void testGetRecipeById() {
        HttpEntity<Void> entity = new HttpEntity<>(getAuthHeaders(TEST_USER_ID));

        ResponseEntity<RecipeResponse> response = restTemplate.exchange(
                getBaseUrl() + "/" + recipeId,
                HttpMethod.GET,
                entity,
                RecipeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(recipeId);
        assertThat(response.getBody().getTitle()).isEqualTo("Test Recipe 1");
    }

    @Test
    @Order(4)
    @DisplayName("Get recipe by ID - should return 403 for non-owner")
    void testGetRecipeByIdForbidden() {
        HttpEntity<Void> entity = new HttpEntity<>(getAuthHeaders(OTHER_USER_ID));

        ResponseEntity<String> response = restTemplate.exchange(
                getBaseUrl() + "/" + recipeId,
                HttpMethod.GET,
                entity,
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(5)
    @DisplayName("Get user recipes - should return all user's recipes")
    void testGetUserRecipes() {
        // Create a second recipe
        CreateRecipeRequest request = createTestRecipe("Test Recipe 2", true);
        HttpEntity<CreateRecipeRequest> createEntity = new HttpEntity<>(request, getAuthHeaders(TEST_USER_ID));
        restTemplate.postForEntity(getBaseUrl(), createEntity, RecipeResponse.class);

        // Get all user recipes
        HttpEntity<Void> getEntity = new HttpEntity<>(getAuthHeaders(TEST_USER_ID));
        ResponseEntity<RecipeResponse[]> response = restTemplate.exchange(
                getBaseUrl(),
                HttpMethod.GET,
                getEntity,
                RecipeResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().length).isGreaterThanOrEqualTo(2);
    }

    @Test
    @Order(6)
    @DisplayName("Update recipe sharing - should make recipe public")
    void testUpdateRecipeSharing() {
        UpdateSharingRequest sharingRequest = new UpdateSharingRequest();
        sharingRequest.setIsPublic(true);
        HttpEntity<UpdateSharingRequest> entity = new HttpEntity<>(sharingRequest, getAuthHeaders(TEST_USER_ID));

        ResponseEntity<RecipeResponse> response = restTemplate.exchange(
                getBaseUrl() + "/" + recipeId + "/sharing",
                HttpMethod.PATCH,
                entity,
                RecipeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isPublic()).isTrue();
    }

    @Test
    @Order(7)
    @DisplayName("Get recipe after sharing - should persist public status")
    void testGetRecipeAfterSharing() {
        HttpEntity<Void> entity = new HttpEntity<>(getAuthHeaders(TEST_USER_ID));

        ResponseEntity<RecipeResponse> response = restTemplate.exchange(
                getBaseUrl() + "/" + recipeId,
                HttpMethod.GET,
                entity,
                RecipeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isPublic()).isTrue();
    }

    @Test
    @Order(8)
    @DisplayName("Get public recipes - should return recipes marked as public")
    void testGetPublicRecipes() {
        ResponseEntity<PagedRecipeResponse> response = restTemplate.getForEntity(
                getBaseUrl() + "/public",
                PagedRecipeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getRecipes()).isNotNull();

        // Find our recipe in the public list
        boolean found = response.getBody().getRecipes().stream()
                .anyMatch(r -> r.getId().equals(recipeId) && r.isPublic());

        assertThat(found).isTrue();
    }

    @Test
    @Order(9)
    @DisplayName("Update recipe sharing - should make recipe private again")
    void testUpdateRecipeSharingToPrivate() {
        UpdateSharingRequest sharingRequest = new UpdateSharingRequest();
        sharingRequest.setIsPublic(false);
        HttpEntity<UpdateSharingRequest> entity = new HttpEntity<>(sharingRequest, getAuthHeaders(TEST_USER_ID));

        ResponseEntity<RecipeResponse> response = restTemplate.exchange(
                getBaseUrl() + "/" + recipeId + "/sharing",
                HttpMethod.PATCH,
                entity,
                RecipeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isPublic()).isFalse();
    }

    @Test
    @Order(10)
    @DisplayName("Get recipe after unsharing - should persist private status")
    void testGetRecipeAfterUnsharing() {
        HttpEntity<Void> entity = new HttpEntity<>(getAuthHeaders(TEST_USER_ID));

        ResponseEntity<RecipeResponse> response = restTemplate.exchange(
                getBaseUrl() + "/" + recipeId,
                HttpMethod.GET,
                entity,
                RecipeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isPublic()).isFalse();
    }

    @Test
    @Order(11)
    @DisplayName("Update recipe - should update all fields")
    void testUpdateRecipe() {
        CreateRecipeRequest updateRequest = createTestRecipe("Updated Recipe Title", false);
        updateRequest.setDescription("Updated description");
        updateRequest.setPrepTime(20);

        HttpEntity<CreateRecipeRequest> entity = new HttpEntity<>(updateRequest, getAuthHeaders(TEST_USER_ID));

        ResponseEntity<RecipeResponse> response = restTemplate.exchange(
                getBaseUrl() + "/" + recipeId,
                HttpMethod.PUT,
                entity,
                RecipeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Updated Recipe Title");
        assertThat(response.getBody().getDescription()).isEqualTo("Updated description");
        assertThat(response.getBody().getPrepTime()).isEqualTo(20);
    }

    @Test
    @Order(12)
    @DisplayName("Update recipe - should return 403 for non-owner")
    void testUpdateRecipeForbidden() {
        CreateRecipeRequest updateRequest = createTestRecipe("Hacked Title", false);
        HttpEntity<CreateRecipeRequest> entity = new HttpEntity<>(updateRequest, getAuthHeaders(OTHER_USER_ID));

        ResponseEntity<String> response = restTemplate.exchange(
                getBaseUrl() + "/" + recipeId,
                HttpMethod.PUT,
                entity,
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(13)
    @DisplayName("Delete recipe - should return 403 for non-owner")
    void testDeleteRecipeForbidden() {
        HttpEntity<Void> entity = new HttpEntity<>(getAuthHeaders(OTHER_USER_ID));

        ResponseEntity<Void> response = restTemplate.exchange(
                getBaseUrl() + "/" + recipeId,
                HttpMethod.DELETE,
                entity,
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(14)
    @DisplayName("Delete recipe - should return 204 for owner")
    void testDeleteRecipe() {
        HttpEntity<Void> entity = new HttpEntity<>(getAuthHeaders(TEST_USER_ID));

        ResponseEntity<Void> response = restTemplate.exchange(
                getBaseUrl() + "/" + recipeId,
                HttpMethod.DELETE,
                entity,
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @Order(15)
    @DisplayName("Get deleted recipe - should return 404")
    void testGetDeletedRecipe() {
        HttpEntity<Void> entity = new HttpEntity<>(getAuthHeaders(TEST_USER_ID));

        ResponseEntity<String> response = restTemplate.exchange(
                getBaseUrl() + "/" + recipeId,
                HttpMethod.GET,
                entity,
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(16)
    @DisplayName("Create recipe with validation errors - should return 400")
    void testCreateRecipeWithValidationErrors() {
        CreateRecipeRequest request = new CreateRecipeRequest();
        // Missing required fields
        request.setTitle(""); // Empty title

        HttpEntity<CreateRecipeRequest> entity = new HttpEntity<>(request, getAuthHeaders(TEST_USER_ID));

        ResponseEntity<String> response = restTemplate.postForEntity(
                getBaseUrl(),
                entity,
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(17)
    @DisplayName("Swagger UI - should be accessible")
    void testSwaggerUI() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/swagger-ui.html",
                String.class);

        // Swagger UI redirects, so we accept either 200 or 3xx
        assertThat(response.getStatusCode().is2xxSuccessful() || response.getStatusCode().is3xxRedirection())
                .isTrue();
    }

    @Test
    @Order(18)
    @DisplayName("OpenAPI spec - should be accessible")
    void testOpenApiSpec() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/v3/api-docs",
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("openapi");
    }

    @Test
    @Order(19)
    @DisplayName("Like recipe - created recipe should have likeCount=0 and isLikedByCurrentUser=false initially")
    void testLikeCountInitialState() {
        CreateRecipeRequest request = createTestRecipe("Like Test Recipe", false);
        HttpEntity<CreateRecipeRequest> entity = new HttpEntity<>(request, getAuthHeaders(TEST_USER_ID));

        ResponseEntity<RecipeResponse> response = restTemplate.postForEntity(
                getBaseUrl(),
                entity,
                RecipeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getLikeCount()).isEqualTo(0);
        assertThat(response.getBody().isLikedByCurrentUser()).isFalse();
    }
}
