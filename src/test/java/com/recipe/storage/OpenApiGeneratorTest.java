package com.recipe.storage;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.recipe.storage.config.MockFirebaseConfig;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "auth.enabled=false")
@Import(MockFirebaseConfig.class)
class OpenApiGeneratorTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void generateOpenApi() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        String content = result.getResponse().getContentAsString();

        // Ensure target directory exists
        Path targetPath = Paths.get("target");
        if (!Files.exists(targetPath)) {
            Files.createDirectories(targetPath);
        }

        Files.writeString(targetPath.resolve("openapi.json"), content);
    }

    @Test
    void documentsAuthenticatedSelfProfileContract() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode openApi = new ObjectMapper().readTree(result.getResponse().getContentAsString());
        JsonNode selfProfile = openApi.path("paths").path("/api/users/me/profile");

        assertTrue(selfProfile.has("get"));
        assertTrue(selfProfile.has("put"));
        assertTrue(selfProfile.path("get").path("responses").has("200"));
        assertTrue(selfProfile.path("put").path("responses").has("400"));
        assertTrue(selfProfile.path("put").path("requestBody").isObject());
    }
}
