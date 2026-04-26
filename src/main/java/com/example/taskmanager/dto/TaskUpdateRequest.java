package com.example.taskmanager.dto;

import com.example.taskmanager.entity.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * TaskUpdateRequest — DTO for PUT /api/v1/tasks/{id}
 * ====================================================
 * Represents the JSON body a client sends when replacing an existing task.
 *
 * WHY A SEPARATE DTO FROM TaskCreateRequest?
 * -------------------------------------------
 * PUT semantics mean "replace the entire resource with this data". Therefore:
 *   - title and description are required (can't leave them undefined in a PUT)
 *   - status IS included (the client transitions the task's workflow state)
 *
 * In contrast, TaskCreateRequest:
 *   - title and description define the new task
 *   - status is NOT included (always starts as TODO — not client-controlled)
 *
 * Using one DTO for both operations would require awkward nullable fields
 * and conditional validation logic. Separate DTOs keep validation clean.
 *
 * PATCH VS PUT:
 * This application implements PUT (full replacement). A PATCH endpoint
 * (partial update) would use a DTO where all fields are Optional<T>,
 * allowing the client to send only the fields they want to change.
 * A PATCH for tasks might look like:
 *
 *   PATCH /api/v1/tasks/{id}
 *   { "status": "IN_PROGRESS" }  // only change the status, leave title alone
 *
 * For simplicity this application omits PATCH, but the pattern is worth knowing.
 */
public record TaskUpdateRequest(

        /**
         * New title — required for a PUT (full replacement).
         * Must not be blank and must not exceed the database column size.
         */
        @NotBlank(message = "Title is required and must not be blank")
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        /**
         * New description — optional, can be null to clear the existing value.
         * If the client sends { "title": "foo", "description": null }, the
         * existing description will be replaced with null (cleared).
         */
        @Size(max = 5000, message = "Description must not exceed 5000 characters")
        String description,

        /**
         * New status — required for a PUT.
         *
         * @NotNull (not @NotBlank) because TaskStatus is an enum, not a String.
         * Jackson will throw HttpMessageNotReadableException if the client sends
         * an invalid status string (e.g., "INVALID"), which our GlobalExceptionHandler
         * catches and returns as a 400 with a helpful message.
         */
        @NotNull(message = "Status is required")
        TaskStatus status

) {}
