package com.example.taskmanager.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenApiConfig — API Documentation Configuration
 * =================================================
 * SpringDoc reads your @RestController classes and auto-generates an
 * OpenAPI 3.0 specification. This config class adds metadata and custom
 * configuration on top of that auto-generated spec.
 *
 * GENERATED ENDPOINTS (available after starting the app):
 *   http://localhost:8080/swagger-ui.html    → Interactive Swagger UI
 *   http://localhost:8080/v3/api-docs        → Raw OpenAPI JSON spec
 *   http://localhost:8080/v3/api-docs.yaml   → Raw OpenAPI YAML spec
 *
 * The JSON/YAML spec can be imported into:
 *   - Postman (for API testing)
 *   - Insomnia (for API testing)
 *   - Client code generators (openapi-generator for TypeScript, Python, etc.)
 *   - API gateways (AWS API Gateway, Kong)
 *
 * ADDING DOCUMENTATION TO ENDPOINTS:
 * SpringDoc picks up annotations from your controller:
 *
 *   @Operation(summary = "Create a task", description = "Creates a new task with TODO status")
 *   @ApiResponse(responseCode = "201", description = "Task created successfully")
 *   @ApiResponse(responseCode = "400", description = "Validation failed")
 *   public ResponseEntity<TaskResponse> createTask(...) { ... }
 *
 * And on DTOs:
 *   @Schema(description = "The task title", example = "Write unit tests")
 *   String title
 *
 * These annotations are optional — SpringDoc works without them using
 * class/field names and validation constraints.
 */
@Configuration
public class OpenApiConfig {

    /**
     * @Value("${spring.application.name:task-manager}"):
     *   Injects the value of the property "spring.application.name" from
     *   application.yml. The ":task-manager" part is the default value used
     *   if the property is not defined. This avoids hardcoding the app name.
     */
    @Value("${spring.application.name:task-manager}")
    private String applicationName;

    /**
     * OpenAPI bean — customises the generated API specification.
     *
     * Everything added here appears in the Swagger UI header and the raw spec.
     * This is useful for:
     *   - API consumers who need contact info or documentation links
     *   - API gateway imports that need server URLs
     *   - Client code generators that use the info block for package naming
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(List.of(
                        // Local development server
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local Development"),
                        // Placeholder for production — replace with your actual URL
                        new Server()
                                .url("https://api.yourdomain.com")
                                .description("Production")
                ))
                // ── Security Scheme Definition ────────────────────────────
                // Defines a reusable security scheme that can be applied to
                // individual endpoints. Currently commented out because OAuth2
                // is not yet enabled. When you enable OAuth2 (see SecurityConfig),
                // uncomment this block and add @SecurityRequirement("bearerAuth")
                // to your controller methods.
                //
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter your JWT token from your OAuth2 provider. " +
                                                "Example: 'eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...'")
                        )
                );
                // ── To require Bearer auth globally: ─────────────────────
                // .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                //
                // ── To require it per-endpoint: ───────────────────────────
                // Add @SecurityRequirement(name = "bearerAuth") to the method
    }

    /**
     * Builds the Info object for the OpenAPI spec.
     * This appears as the header in the Swagger UI and in the spec's "info" block.
     */
    private Info apiInfo() {
        return new Info()
                .title("Task Manager API")
                .version("1.0.0")
                .description("""
                        A RESTful API for managing tasks. Demonstrates Spring Boot best practices
                        including layered architecture, DTO pattern, global exception handling,
                        and extensibility for OAuth2, Docker, Kubernetes, and Terraform.

                        **Features:**
                        - Full CRUD for tasks
                        - Status-based filtering (TODO, IN_PROGRESS, DONE)
                        - Pagination and sorting support
                        - Structured error responses

                        **Getting Started:**
                        Use the H2 console at `/h2-console` to inspect the in-memory database.
                        """)
                .contact(new Contact()
                        .name("Your Team")
                        .email("dev@yourdomain.com")
                        .url("https://github.com/your-org/task-manager"))
                .license(new License()
                        .name("MIT License")
                        .url("https://opensource.org/licenses/MIT"));
    }
}
