package com.example.taskmanager.exception;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * ErrorResponse — Structured API Error Body
 * ===========================================
 * This record defines the JSON shape of all error responses from this API.
 * Every error, regardless of type, returns the same structure so API clients
 * can parse errors consistently.
 *
 * EXAMPLE JSON (validation error):
 * {
 *   "status": 400,
 *   "error": "Bad Request",
 *   "message": "Validation failed for 2 fields",
 *   "path": "/api/v1/tasks",
 *   "timestamp": "2024-01-15T10:30:00",
 *   "fieldErrors": {
 *     "title": "Title is required and must not be blank",
 *     "description": "Description must not exceed 5000 characters"
 *   }
 * }
 *
 * EXAMPLE JSON (not found error):
 * {
 *   "status": 404,
 *   "error": "Not Found",
 *   "message": "Task not found with id: 42",
 *   "path": "/api/v1/tasks/42",
 *   "timestamp": "2024-01-15T10:31:00",
 *   "fieldErrors": null
 * }
 *
 * WHY A STANDARD ERROR SHAPE MATTERS:
 * Without a consistent error format, API clients must handle dozens of different
 * response shapes. A unified structure means one error-parsing routine on the
 * client side works for all errors.
 *
 * This follows the RFC 7807 "Problem Details for HTTP APIs" spirit, though
 * not the exact format. For strict RFC 7807 compliance, use the
 * spring-webmvc "ProblemDetail" class (available since Spring 6 / Boot 3).
 *
 * FIELD DESCRIPTIONS:
 *   status     — the HTTP status code (redundant with the HTTP response code,
 *                but convenient for clients that read the response body only)
 *   error      — the HTTP status reason phrase ("Bad Request", "Not Found", etc.)
 *   message    — a human-readable explanation of what went wrong
 *   path       — the request URI that produced this error (useful for debugging)
 *   timestamp  — when the error occurred (useful for correlating with server logs)
 *   fieldErrors— populated for 400 validation errors only; maps field name to the
 *                validation message. Null for all other error types.
 */
public record ErrorResponse(
        int status,
        String error,
        String message,
        String path,
        LocalDateTime timestamp,
        Map<String, String> fieldErrors
) {

    /**
     * Factory method for errors WITHOUT field-level details (404, 500, etc.)
     * Having factory methods makes the calling code in GlobalExceptionHandler
     * more readable than calling the full constructor with a null fieldErrors.
     */
    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(status, error, message, path, LocalDateTime.now(), null);
    }

    /**
     * Factory method for validation errors WITH field-level details (400).
     */
    public static ErrorResponse ofValidation(String message, String path, Map<String, String> fieldErrors) {
        return new ErrorResponse(400, "Bad Request", message, path, LocalDateTime.now(), fieldErrors);
    }
}
