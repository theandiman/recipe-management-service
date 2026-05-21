package com.recipe.storage.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "auth.enabled=false",
        "firestore.collection.recipes=test-recipes",
        "firestore.collection.users=test-users"
})
class UserProfileControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getUserProfile_NoAuthHeader_IsAllowed() throws Exception {
        // Endpoint is public: no Authorization header needed.
        // Without Firestore configured the service returns 503.
        mockMvc.perform(get("/api/users/some-uid/profile"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void getUserProfile_WithAuthHeader_IsAllowed() throws Exception {
        // Even with an auth header the endpoint remains accessible (no token required).
        // Without Firestore configured the service returns 503.
        mockMvc.perform(get("/api/users/some-uid/profile")
                .header("Authorization", "Bearer some-token"))
                .andExpect(status().isServiceUnavailable());
    }
}
