package com.recipe.storage.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "auth.enabled=false",
    "firestore.collection.recipes=test-recipes",
    "firestore.collection.users=test-users"
})
class UserProfilePrivacyIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void getPublicProfile_WithoutFirestore_ReturnsServiceUnavailable() throws Exception {
    mockMvc.perform(get("/api/users/user1/profile"))
        .andExpect(status().isServiceUnavailable());
  }
}
