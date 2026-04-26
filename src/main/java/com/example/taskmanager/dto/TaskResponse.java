package com.example.taskmanager.dto;

import com.example.taskmanager.entity.TaskStatus;

import java.time.LocalDateTime;

/**
 * TaskResponse — DTO for all API responses containing a task
 * ===========================================================
 * This record represents the JSON structure returned to the client.
 * It mirrors the full state of a Task, including server-managed fields
 * (id, createdAt, updatedAt) that clients cannot set themselves.
 *
 * RESPONSE DTO DESIGN PRINCIPLES:
 * ---------------------------------
 * 1. Include what clients need, nothing more.
 *    - No JPA annotations, no Hibernate proxy fields, no lazy-loaded collections.
 *    - No sensitive internal identifiers or implementation details.
 *
 * 2. Response shape should be stable even when the entity changes.
 *    Example: if you add an "internalAuditLog" field to the Task entity,
 *    the TaskResponse intentionally does NOT include it. The response
 *    shape is your public API contract.
 *
 * 3. Use value types (String, Long, enums, LocalDateTime) — never JPA entities.
 *    Returning a Task entity from a controller can trigger lazy-loading
 *    outside of a transaction (LazyInitializationException) and serialises
 *    internal implementation details.
 *
 * JAVA RECORD — JSON SERIALISATION:
 * Jackson serialises records by reading the component accessor methods.
 * The JSON field names will be: "id", "title", "description", "status",
 * "createdAt", "updatedAt" — matching the component names exactly.
 *
 * LocalDateTime is serialised as an ISO-8601 array by default:
 *   [2024, 1, 15, 10, 30, 0]
 * To get a string format ("2024-01-15T10:30:00"), add to application.yml:
 *   spring.jackson.serialization.write-dates-as-timestamps: false
 *
 * EXAMPLE JSON OUTPUT:
 * {
 *   "id": 1,
 *   "title": "Write unit tests",
 *   "description": "Cover the service layer with Mockito",
 *   "status": "TODO",
 *   "createdAt": "2024-01-15T10:30:00",
 *   "updatedAt": "2024-01-15T10:30:00"
 * }
 */
public record TaskResponse(

        Long id,
        String title,
        String description,
        TaskStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt

) {}
