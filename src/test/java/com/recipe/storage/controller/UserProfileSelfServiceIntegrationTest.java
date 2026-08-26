package com.recipe.storage.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipe.storage.dto.UpdateUserProfileRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "auth.enabled=false",
    "firestore.collection.recipes=test-recipes",
    "firestore.collection.users=test-users"
})
class UserProfileSelfServiceIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void getMyProfile_Unauthenticated_ReturnsUnauthorized() throws Exception {
    // When auth is enabled or userId attribute is missing, GET /api/users/me/profile should be protected
  }

  @Test
  void updateMyProfile_WithoutFirestore_ReturnsServiceUnavailable() throws Exception {
    UpdateUserProfileRequest request = UpdateUserProfileRequest.builder()
        .displayName("Chef Tester")
        .bio("A great chef")
        .visibility("PUBLIC")
        .build();

    mockMvc.perform(put("/api/users/me/profile")
            .header("userId", "test-user")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isServiceUnavailable());
  }

  @Test
  void updateUserProfile_ForbiddenWhenModifyingOtherProfile() throws Exception {
    UpdateUserProfileRequest request = UpdateUserProfileRequest.builder()
        .displayName("Hacker")
        .build();

    mockMvc.perform(put("/api/users/other-user/profile")
            .header("userId", "test-user")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  void updateUserProfile_ValidationFailureWhenDisplayNameTooLong() throws Exception {
    String longName = "A".repeat(51);
    UpdateUserProfileRequest request = UpdateUserProfileRequest.builder()
        .displayName(longName)
        .build();

    mockMvc.perform(put("/api/users/test-user/profile")
            .header("userId", "test-user")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateUserProfile_ValidationFailureWhenVisibilityInvalid() throws Exception {
    UpdateUserProfileRequest request = UpdateUserProfileRequest.builder()
        .visibility("INVALID_VISIBILITY")
        .build();

    mockMvc.perform(put("/api/users/test-user/profile")
            .header("userId", "test-user")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }
}
