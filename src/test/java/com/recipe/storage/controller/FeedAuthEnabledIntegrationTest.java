package com.recipe.storage.controller;

import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test verifying that the feed endpoint requires authentication
 * when auth is enabled.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "auth.enabled=true",
        "firestore.collection.recipes=test-recipes",
        "firestore.collection.users=test-users",
        "firestore.collection.follows=test-follows"
})
class FeedAuthEnabledIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FirebaseApp firebaseApp;

    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
    private Firestore mockFirestore;

    @MockitoBean
    private FirebaseAuth firebaseAuth;

    @Test
    void getFeed_NoAuthHeader_Returns401() throws Exception {
        mockMvc.perform(get("/api/feed"))
                .andExpect(status().isUnauthorized());
    }
}
