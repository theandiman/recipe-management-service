package com.recipe.storage;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
}
