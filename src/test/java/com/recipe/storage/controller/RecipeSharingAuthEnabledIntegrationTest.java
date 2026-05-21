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

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
                "auth.enabled=true",
                "firestore.collection.recipes=test-recipes"
})
class RecipeSharingAuthEnabledIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private FirebaseApp firebaseApp;

        @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
        private Firestore mockFirestore;

        @MockitoBean
        private FirebaseAuth firebaseAuth;

        @Test
        void getPublicRecipe_NoAuthHeader_IsAllowed_WhenAuthEnabled() throws Exception {
                // Endpoint should be reachable without Authorization header even when auth is enabled.
                // Without Firestore the service returns 404 (not 401/403).
                mockMvc.perform(get("/api/recipes/some-recipe-id/public"))
                                .andExpect(status().isNotFound());
        }
}
