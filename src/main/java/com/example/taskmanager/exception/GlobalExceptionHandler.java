package com.example.taskmanager.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GlobalExceptionHandler — Centralised Exception-to-HTTP-Response Mapping
 * =========================================================================
 * Without this class, unhandled exceptions from controllers and services would
 * propagate to Spring MVC's default error handling, which returns a generic
 * "white label error page" or a JSON body with inconsistent fields.
 *
 * @RestControllerAdvice:
 *   A specialisation of @ControllerAdvice + @ResponseBody. It declares that
 *   this class is a cross-cutting concern that applies to ALL @RestController
 *   classes in the application. Methods annotated with @ExceptionHandler inside
 *   this class intercept exceptions thrown by any controller method.
 *
 *   HOW IT WORKS:
 *   When a controller method throws an exception, Spring MVC:
 *     1. Searches all @ControllerAdvice classes for an @ExceptionHandler
 *        that matches the thrown exception type (exact match first,
 *        then superclass match, then catch-all Exception.class)
 *     2. Calls the matched handler method
 *     3. Uses its return value as the HTTP response
 *
 * EXCEPTION HIERARCHY:
 *   All handlers are ordered from most-specific to least-specific.
 *   Spring will match the most specific handler available.
 *
 * SECURITY NOTE:
 *   The catch-all handler (handleAll) intentionally returns a generic message
 *   and never exposes the actual exception message or stack trace. Exposing
 *   internal details (class names, SQL errors, file paths) is an information
 *   disclosure vulnerability (OWASP A05:2021).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles 404 — Task Not Found
     *
     * @ExceptionHandler(TaskNotFoundException.class) tells Spring to call this
     * method when any controller throws a TaskNotFoundException.
     *
     * HttpServletRequest request — Spring injects this automatically.
     * We use it to include the request URI in the error response, helping
     * clients and support teams identify which URL triggered the error.
     *
     * ResponseEntity<ErrorResponse> — wraps the response body with full control
     * over the HTTP status code. We could also use @ResponseStatus on the method,
     * but ResponseEntity makes the status explicit in the code.
     */
    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTaskNotFound(
            TaskNotFoundException ex,
            HttpServletRequest request) {

        ErrorResponse body = ErrorResponse.of(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    /**
     * Handles 400 — Bean Validation Failures
     *
     * MethodArgumentNotValidException is thrown by Spring MVC when a
     * @RequestBody parameter annotated with @Valid fails validation.
     *
     * We extract all field-level errors and build a map of:
     *   fieldName → validation message
     *
     * Example: { "title": "Title is required and must not be blank" }
     *
     * getBindingResult() — the result of the binding and validation process.
     * getFieldErrors()   — a list of FieldError objects, one per failing constraint.
     *
     * LinkedHashMap preserves insertion order, so field errors appear in the
     * order they are defined in the DTO, which is more predictable for clients.
     *
     * In case of multiple violations on the same field (e.g., both @NotBlank and
     * @Size fail), the last error wins. For production, you might want a
     * Map<String, List<String>> instead to capture all messages per field.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        String message = String.format(
                "Validation failed for %d field(s). See 'fieldErrors' for details.",
                fieldErrors.size()
        );

        ErrorResponse body = ErrorResponse.ofValidation(message, request.getRequestURI(), fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Handles 400 — Malformed or Unreadable Request Body
     *
     * Thrown when Jackson cannot parse the request body:
     *   - Invalid JSON syntax: { "title": }
     *   - Invalid enum value: { "status": "INVALID_STATUS" }
     *   - Type mismatch:  { "id": "not-a-number" }
     *
     * We return a 400 with a helpful message instead of leaking the full
     * Jackson parsing error (which can contain internal class names).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {

        ErrorResponse body = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Request body is missing, malformed, or contains invalid values. " +
                "Check that your JSON is well-formed and enum fields contain valid values.",
                request.getRequestURI()
        );

        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Handles 400 — Path Variable or Request Parameter Type Mismatch
     *
     * Thrown when a path variable can't be converted to the expected type.
     * Example: GET /api/v1/tasks/abc — "abc" can't be converted to Long.
     *
     * Without this handler, Spring returns a 400 with a confusing message
     * like "Failed to convert value of type 'String' to required type 'Long'".
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {

        String message = String.format(
                "Parameter '%s' must be of type %s",
                ex.getName(),
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown"
        );

        ErrorResponse body = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                message,
                request.getRequestURI()
        );

        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Handles 405 — HTTP Method Not Allowed
     *
     * Thrown when a client calls a URL with an unsupported HTTP method.
     * Example: DELETE /api/v1/tasks (list endpoint doesn't support DELETE).
     *
     * Spring includes an "Allow" header in the response indicating which
     * methods ARE supported — we preserve this by using ResponseEntity
     * with the exception's own supported methods info.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {

        ErrorResponse body = ErrorResponse.of(
                HttpStatus.METHOD_NOT_ALLOWED.value(),
                HttpStatus.METHOD_NOT_ALLOWED.getReasonPhrase(),
                String.format("HTTP method '%s' is not supported for this endpoint. " +
                              "Supported methods: %s", ex.getMethod(), ex.getSupportedHttpMethods()),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body);
    }

    /**
     * Catch-All Handler — Handles 500 — Unexpected Internal Errors
     *
     * This is the safety net. Any exception not matched by the handlers above
     * falls through to here. It returns a generic 500 response without
     * exposing the actual exception details to the client.
     *
     * IMPORTANT: Log the actual exception here so developers can diagnose it.
     * In a real application, use a logging framework (SLF4J + Logback, already
     * included by spring-boot-starter):
     *
     *   private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
     *   log.error("Unexpected error handling request to {}", request.getRequestURI(), ex);
     *
     * We use System.err here for simplicity to avoid importing the logger.
     * In production code, ALWAYS use a proper logger.
     *
     * SECURITY NOTE: Never include ex.getMessage() or ex.getClass().getName()
     * in the response body. Attacker-controlled inputs may appear in exception
     * messages, causing information disclosure.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAll(
            Exception ex,
            HttpServletRequest request) {

        // Log the full exception server-side for diagnosis.
        // Replace with: log.error("Unhandled exception", ex);
        System.err.println("[ERROR] Unhandled exception at " + request.getRequestURI() + ": " + ex);

        ErrorResponse body = ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "An unexpected error occurred. Please contact support if this persists.",
                request.getRequestURI()
        );

        return ResponseEntity.internalServerError().body(body);
    }
}
