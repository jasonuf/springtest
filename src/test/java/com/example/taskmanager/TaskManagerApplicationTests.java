package com.example.taskmanager;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * TaskManagerApplicationTests — Application Context Smoke Test
 * =============================================================
 * This test class contains a single test that does almost nothing on its own,
 * yet is one of the most valuable tests in the project.
 *
 * @SpringBootTest:
 *   Tells Spring Boot Test to start the FULL application context (all beans,
 *   all auto-configuration, all @Configuration classes). This simulates what
 *   happens when you run the application for real.
 *
 *   If ANY of the following are broken, this test will fail:
 *   - A Spring bean has an unsatisfied dependency (missing @Bean)
 *   - A @Configuration class has invalid syntax or logic
 *   - application.yml has invalid properties
 *   - MapStruct failed to generate a mapper implementation
 *   - The database schema doesn't match the entities (with ddl-auto=validate)
 *   - Any other startup-time failure
 *
 * THE CONTEXT LOADS TEST:
 *   The contextLoads() method has NO assertions — it simply starts and stops
 *   the application context. If context startup throws any exception, the test fails.
 *   This catches wiring errors that only appear at runtime, not during compilation.
 *
 * WHEN TO RUN:
 *   This test runs as part of every `mvn test` execution. It's deliberately
 *   slow (starts the full context including Tomcat) compared to unit tests.
 *   CI pipelines should always run it; local developers might skip it with
 *   -Dtest=TaskControllerTest during rapid iteration.
 *
 * SPRING BOOT TEST SLICES (for faster tests):
 *   If you don't need the full context, Spring Boot provides "test slices":
 *   @WebMvcTest(TaskController.class) — only MVC layer, no JPA
 *   @DataJpaTest                      — only JPA layer, no MVC
 *   @JsonTest                         — only Jackson serialisation
 *   These start much faster. See TaskControllerTest and TaskServiceImplTest.
 */
@SpringBootTest
class TaskManagerApplicationTests {

    /**
     * Verifies that the Spring application context starts without errors.
     * No explicit assertions needed — a startup exception fails the test automatically.
     */
    @Test
    void contextLoads() {
        // Intentionally empty — if Spring fails to start, the test fails with
        // the full startup exception, which is the diagnostic we need.
    }
}
