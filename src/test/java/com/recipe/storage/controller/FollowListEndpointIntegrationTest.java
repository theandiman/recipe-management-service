package com.recipe.storage.controller;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the GET followers and following list endpoints.
 *
 * <p>Uses an explicit Firestore mock so the full controller-to-service wiring
 * can be exercised end-to-end without a real Firestore connection.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "auth.enabled=false",
        "firestore.collection.recipes=test-recipes",
        "firestore.collection.users=test-users",
        "firestore.collection.follows=test-follows"
})
class FollowListEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Firestore mockFirestore;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void stubFirestoreForEmptyQueries() throws Exception {
        // Build a mock chain for follows collection queries
        CollectionReference followsCol = mock(CollectionReference.class);
        DocumentReference anyUserDoc = mock(DocumentReference.class);
        CollectionReference anySubcol = mock(CollectionReference.class);
        Query orderedQuery = mock(Query.class);
        Query limitedQuery = mock(Query.class);
        ApiFuture<QuerySnapshot> queryFuture = mock(ApiFuture.class);
        QuerySnapshot emptySnapshot = mock(QuerySnapshot.class);

        when(mockFirestore.collection("test-follows")).thenReturn(followsCol);
        when(followsCol.document(anyString())).thenReturn(anyUserDoc);
        when(anyUserDoc.collection(anyString())).thenReturn(anySubcol);
        when(anySubcol.orderBy(anyString(), any(Query.Direction.class))).thenReturn(orderedQuery);
        when(orderedQuery.limit(anyInt())).thenReturn(limitedQuery);
        when(limitedQuery.get()).thenReturn(queryFuture);
        when(queryFuture.get()).thenReturn(emptySnapshot);
        when(emptySnapshot.isEmpty()).thenReturn(true);
        when(emptySnapshot.getDocuments()).thenReturn(Collections.emptyList());
    }

    @Test
    void getFollowers_Returns200WithEmptyList() throws Exception {
        mockMvc.perform(get("/api/users/some-user/followers"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.users").isArray())
                .andExpect(jsonPath("$.users").isEmpty())
                .andExpect(jsonPath("$.nextPageToken").doesNotExist());
    }

    @Test
    void getFollowing_Returns200WithEmptyList() throws Exception {
        mockMvc.perform(get("/api/users/some-user/following"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.users").isArray())
                .andExpect(jsonPath("$.users").isEmpty())
                .andExpect(jsonPath("$.nextPageToken").doesNotExist());
    }

    @Test
    void getFollowers_WithPageSizeParam_Returns200() throws Exception {
        mockMvc.perform(get("/api/users/some-user/followers")
                .param("pageSize", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users").isArray());
    }

    @Test
    void getFollowers_InvalidPageSize_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/users/some-user/followers")
                .param("pageSize", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getFollowing_InvalidPageSize_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/users/some-user/following")
                .param("pageSize", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getFollowers_NoAuthRequired_AccessibleWithoutToken() throws Exception {
        // Endpoint should be accessible without any Authorization header
        mockMvc.perform(get("/api/users/public-user/followers"))
                .andExpect(status().isOk());
    }

    @Test
    void getFollowing_NoAuthRequired_AccessibleWithoutToken() throws Exception {
        mockMvc.perform(get("/api/users/public-user/following"))
                .andExpect(status().isOk());
    }
}
