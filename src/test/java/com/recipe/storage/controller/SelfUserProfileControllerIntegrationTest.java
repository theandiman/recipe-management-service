package com.recipe.storage.controller;

import com.recipe.storage.dto.ProfileVisibility;
import com.recipe.storage.dto.SelfUserProfileResponse;
import com.recipe.storage.dto.UpdateUserProfileRequest;
import com.recipe.storage.service.FollowService;
import com.recipe.storage.service.UserProfileService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "auth.enabled=false",
        "firestore.collection.recipes=test-recipes",
        "firestore.collection.users=test-users",
        "firestore.collection.follows=test-follows"
})
class SelfUserProfileControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserProfileService userProfileService;

    @MockitoBean
    private FollowService followService;

    @Test
    void updateSelfProfile_UsesAuthenticatedCallerRatherThanClientSuppliedIdentity() throws Exception {
        SelfUserProfileResponse profile = profileResponse("profile-owner");
        when(userProfileService.updateSelfProfile(eq("profile-owner"), any()))
                .thenReturn(profile);

        mockMvc.perform(put("/api/users/me/profile")
                        .header("userId", "profile-owner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Chef Andy",
                                  "bio": "Pasta enthusiast",
                                  "avatarUrl": "https://example.com/andy.png",
                                  "visibility": "PRIVATE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uid").value("profile-owner"))
                .andExpect(jsonPath("$.visibility").value("PUBLIC"));

        ArgumentCaptor<UpdateUserProfileRequest> requestCaptor =
                ArgumentCaptor.forClass(UpdateUserProfileRequest.class);
        verify(userProfileService).updateSelfProfile(eq("profile-owner"), requestCaptor.capture());
        assertEquals("Chef Andy", requestCaptor.getValue().getDisplayName());
        assertEquals(ProfileVisibility.PRIVATE, requestCaptor.getValue().getVisibility());
    }

    @Test
    void updateSelfProfile_RejectsServiceOwnedFields() throws Exception {
        mockMvc.perform(put("/api/users/me/profile")
                        .header("userId", "profile-owner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Chef Andy",
                                  "visibility": "PUBLIC",
                                  "followerCount": 999
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.violations[0].message")
                        .value("contains unsupported profile fields"));

        verifyNoInteractions(userProfileService);
    }

    @Test
    void getSelfProfile_UsesAuthenticatedCaller() throws Exception {
        when(userProfileService.getSelfProfile("profile-owner"))
                .thenReturn(profileResponse("profile-owner"));

        mockMvc.perform(get("/api/users/me/profile").header("userId", "profile-owner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uid").value("profile-owner"))
                .andExpect(jsonPath("$.followerCount").value(3));

        verify(userProfileService).getSelfProfile("profile-owner");
    }

    private SelfUserProfileResponse profileResponse(String uid) {
        return SelfUserProfileResponse.builder()
                .uid(uid)
                .displayName("Chef Andy")
                .bio("Pasta enthusiast")
                .avatarUrl("https://example.com/andy.png")
                .visibility(ProfileVisibility.PUBLIC)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-02-01T00:00:00Z"))
                .followerCount(3L)
                .followingCount(2L)
                .build();
    }
}
