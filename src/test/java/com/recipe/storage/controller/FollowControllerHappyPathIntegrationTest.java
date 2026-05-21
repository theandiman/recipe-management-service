package com.recipe.storage.controller;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Happy-path integration tests for the follow/unfollow endpoints.
 *
 * <p>Uses a mocked Firestore bean so the full controller-to-service wiring can be
 * exercised end-to-end without a real Firestore connection.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "auth.enabled=false",
        "firestore.collection.recipes=test-recipes",
        "firestore.collection.users=test-users",
        "firestore.collection.follows=test-follows"
})
class FollowControllerHappyPathIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
    private Firestore mockFirestore;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void stubFirestore() throws Exception {
        // Stub runTransaction to return a completed future indicating a write occurred.
        ApiFuture<Boolean> txResult = mock(ApiFuture.class);
        when(txResult.get()).thenReturn(Boolean.TRUE);
        when(mockFirestore.runTransaction(any(Transaction.Function.class))).thenReturn(txResult);
    }

    @Test
    void followUser_HappyPath_Returns204() throws Exception {
        // auth.enabled=false injects "test-user" as caller; "other-user" is the target.
        mockMvc.perform(post("/api/users/other-user/follow"))
                .andExpect(status().isNoContent());
    }

    @Test
    void unfollowUser_HappyPath_Returns204() throws Exception {
        mockMvc.perform(delete("/api/users/other-user/follow"))
                .andExpect(status().isNoContent());
    }
}
