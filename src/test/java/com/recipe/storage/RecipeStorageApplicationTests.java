package com.recipe.storage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestPropertySource(properties = {
    "auth.enabled=false",
    "firestore.collection.recipes=test-recipes"
})
class RecipeStorageApplicationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {
        // Verify Spring context loads successfully
        assertNotNull(applicationContext);
        assertTrue(applicationContext.containsBean("recipeService"));
        assertTrue(applicationContext.containsBean("recipeController"));
    }

    @Test
    void applicationStarts() {
        // Verify that the application can start without errors
        // This test ensures the main method and all configurations are valid
        assertNotNull(applicationContext);
        assertNotNull(applicationContext.getBean(RecipeStorageApplication.class));
    }

    @Test
    void actuatorHealthEndpoint_Returns200() {
        // Act
        ResponseEntity<String> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/actuator/health",
            String.class
        );

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}
