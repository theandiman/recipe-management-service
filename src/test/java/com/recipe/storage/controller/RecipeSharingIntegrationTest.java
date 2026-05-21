package com.recipe.storage.controller;

import tools.jackson.databind.ObjectMapper;
import com.recipe.storage.dto.CreateRecipeRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
                "auth.enabled=false",
                "firestore.collection.recipes=test-recipes"
})
class RecipeSharingIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Test
        void createPublicRecipe_Success() throws Exception {
                CreateRecipeRequest request = CreateRecipeRequest.builder()
                                .title("Public Recipe")
                                .description("A public recipe")
                                .ingredients(List.of("Ingredient 1"))
                                .instructions(List.of("Step 1"))
                                .servings(2)
                                .prepTime(10)
                                .cookTime(10)
                                .source("manual")
                                .isPublic(true)
                                .build();

                mockMvc.perform(post("/api/recipes")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .header("X-User-ID", "user123"))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.isPublic").value(true));
        }

        @Test
        void getPublicRecipes_ReturnsOk() throws Exception {
                // Firestore is not mocked, so returns an empty paged envelope
                mockMvc.perform(get("/api/recipes/public"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.recipes").isArray())
                                .andExpect(jsonPath("$.totalCount").value(0));
        }

        @Test
        void getPublicRecipe_NoFirestore_ReturnsServiceUnavailable() throws Exception {
                mockMvc.perform(get("/api/recipes/some-recipe-id/public"))
                                .andExpect(status().isServiceUnavailable());
        }

        @Test
        void likeRecipe_NoFirestore_ReturnsServiceUnavailable() throws Exception {
                mockMvc.perform(post("/api/recipes/some-recipe-id/like")
                                .header("X-User-ID", "user123"))
                                .andExpect(status().isServiceUnavailable());
        }

        @Test
        void unlikeRecipe_NoFirestore_ReturnsServiceUnavailable() throws Exception {
                mockMvc.perform(delete("/api/recipes/some-recipe-id/like")
                                .header("X-User-ID", "user123"))
                                .andExpect(status().isServiceUnavailable());
        }
}
