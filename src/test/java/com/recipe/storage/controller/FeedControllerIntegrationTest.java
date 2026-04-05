package com.recipe.storage.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the {@code GET /api/feed} endpoint.
 *
 * <p>Uses {@code auth.enabled=false} so the filter injects "test-user" as the caller.
 * Without Firestore configured the follow service returns an empty list and the feed
 * service returns an empty paged response.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "auth.enabled=false",
        "firestore.collection.recipes=test-recipes",
        "firestore.collection.users=test-users",
        "firestore.collection.follows=test-follows"
})
class FeedControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getFeed_NoFirestore_ReturnsEmptyFeed() throws Exception {
        // Without Firestore the follow service returns an empty list, so the feed is empty.
        mockMvc.perform(get("/api/feed")
                .header("userId", "test-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipes").isArray())
                .andExpect(jsonPath("$.recipes").isEmpty())
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    void getFeed_WithCustomSize_ReturnsCorrectSize() throws Exception {
        mockMvc.perform(get("/api/feed")
                .param("size", "10")
                .header("userId", "test-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(10));
    }

    @Test
    void getFeed_SizeExceedsMax_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/feed")
                .param("size", "101")
                .header("userId", "test-user"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getFeed_SizeBelowMin_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/feed")
                .param("size", "0")
                .header("userId", "test-user"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getFeed_NoNextPageToken_WhenFeedIsEmpty() throws Exception {
        mockMvc.perform(get("/api/feed")
                .header("userId", "test-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextPageToken").doesNotExist());
    }
}
