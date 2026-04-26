package com.example.taskmanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * TaskCreateRequest — DTO for POST /api/v1/tasks
 * ================================================
 * A DTO (Data Transfer Object) is a simple carrier of data between layers.
 * This class represents the JSON body a client sends when creating a task.
 *
 * WHY A SEPARATE CLASS FROM THE ENTITY?
 * ----------------------------------------
 * 1. SECURITY: The client should not control which fields get persisted.
 *    If you used the Task entity directly, a malicious client could set
 *    the "id" field and overwrite an existing record, or manipulate
 *    "createdAt" timestamps. DTOs let you whitelist exactly what the
 *    client is allowed to provide.
 *
 * 2. VALIDATION SEPARATION: Request DTOs carry validation annotations.
 *    Entities carry persistence constraints. These can differ — for example,
 *    description is @NotBlank in a request but nullable in the database
 *    (we store the entity as-is; we validate at the boundary).
 *
 * 3. API STABILITY: Your API contract (this DTO) can remain stable even
 *    when your database schema changes, and vice versa.
 *
 * JAVA RECORD:
 * This class is a Java record (introduced in Java 16, standard in Java 21).
 * Records are immutable data carriers that automatically provide:
 *   - A constructor for all fields
 *   - Getters named after the fields (title(), description() — no "get" prefix)
 *   - equals(), hashCode(), toString()
 *
 * Records are ideal for DTOs because DTOs should be immutable — once created
 * from the incoming HTTP request, they should not be modified.
 *
 * NOTE ON DESERIALIZATION:
 * Jackson (the JSON library bundled with Spring Boot) can deserialize JSON
 * into records since Jackson 2.12. The JSON field names must match the record
 * component names exactly (or use @JsonProperty to alias them).
 */
public record TaskCreateRequest(

        /**
         * The task title — required, non-empty, max 255 characters.
         *
         * @NotBlank — fails if the value is null, empty (""), or blank ("   ").
         *   This is stricter than @NotNull (which allows "") and @NotEmpty
         *   (which allows "   "). Use @NotBlank for user-facing string fields.
         *
         * @Size(max = 255) — fails if the string length exceeds 255.
         *   Must match the @Column(length = 255) constraint on the entity.
         *   It's better to fail at validation (400 Bad Request) than at the
         *   database layer (500 Internal Server Error with a cryptic message).
         *
         * message = "..." overrides the default validation error message shown
         * to the client. Clear messages reduce support requests.
         */
        @NotBlank(message = "Title is required and must not be blank")
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        /**
         * An optional longer description of the task.
         * No @NotBlank — the client may omit this field entirely.
         * Jackson will set it to null if the JSON field is missing or null.
         *
         * @Size(max = 5000) caps unreasonably large inputs at the API boundary.
         * Without this, a client could send a 100MB description string and
         * waste memory/database resources.
         */
        @Size(max = 5000, message = "Description must not exceed 5000 characters")
        String description

) {}
