package com.recipe.storage.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the follow/unfollow endpoints.
 *
 * <p>With auth.enabled=false the filter injects "test-user" as the caller.
 * Without real Firestore the service returns 503, except for the self-follow
 * case which returns 400 before touching Firestore.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "auth.enabled=false",
        "firestore.collection.recipes=test-recipes",
        "firestore.collection.users=test-users",
        "firestore.collection.follows=test-follows"
})
class FollowControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void followUser_SelfFollow_ReturnsBadRequest() throws Exception {
        // The filter injects userId "test-user" when auth is disabled.
        // Calling POST /api/users/test-user/follow means followerId == followedId.
        mockMvc.perform(post("/api/users/test-user/follow"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void followUser_NoFirestore_ReturnsServiceUnavailable() throws Exception {
        // Without Firestore configured the service returns 503.
        mockMvc.perform(post("/api/users/other-user/follow"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void followUser_WithExplicitUserId_ReturnsServiceUnavailable() throws Exception {
        // Explicit userId header sets the caller; still no Firestore, so 503.
        mockMvc.perform(post("/api/users/other-user/follow")
                .header("userId", "caller-user"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void unfollowUser_SelfUnfollow_ReturnsBadRequest() throws Exception {
        mockMvc.perform(delete("/api/users/test-user/follow"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unfollowUser_NoFirestore_ReturnsServiceUnavailable() throws Exception {
        mockMvc.perform(delete("/api/users/other-user/follow"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void getFollowers_NoFirestore_ReturnsServiceUnavailable() throws Exception {
        mockMvc.perform(get("/api/users/some-user/followers"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void getFollowing_NoFirestore_ReturnsServiceUnavailable() throws Exception {
        mockMvc.perform(get("/api/users/some-user/following"))
                .andExpect(status().isServiceUnavailable());
    }
}
