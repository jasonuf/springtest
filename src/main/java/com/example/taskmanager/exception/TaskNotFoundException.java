package com.example.taskmanager.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * TaskNotFoundException — Domain-specific Runtime Exception
 * ===========================================================
 * Thrown when a task is requested by ID but does not exist in the database.
 * This represents a "not found" condition — a normal business scenario, not
 * a programming error. Therefore it extends RuntimeException rather than
 * Exception (no checked exception overhead).
 *
 * @ResponseStatus(HttpStatus.NOT_FOUND):
 *   When Spring MVC catches an unhandled exception from a @Controller, it
 *   checks if the exception class has @ResponseStatus and uses the specified
 *   HTTP status code. Without our GlobalExceptionHandler, this annotation alone
 *   would return a 404 with Spring's default white-label error page.
 *
 *   WITH our GlobalExceptionHandler (which catches TaskNotFoundException explicitly),
 *   the @ResponseStatus annotation on the exception class is overridden. However,
 *   we keep it as documentation: it signals to any reader that this exception
 *   represents an HTTP 404 condition, even if they haven't read the handler.
 *
 * NAMING CONVENTION:
 *   Exception class names end in "Exception". Domain exceptions are named after
 *   the resource they apply to: TaskNotFoundException, UserNotFoundException, etc.
 *   This makes stack traces immediately readable.
 *
 * WHERE IT IS THROWN:
 *   TaskServiceImpl.getTaskById(), updateTask(), deleteTask() — any operation
 *   that looks up a task by ID first.
 *
 * WHERE IT IS CAUGHT:
 *   GlobalExceptionHandler.handleTaskNotFound() — translated to a structured
 *   JSON error response with status 404.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class TaskNotFoundException extends RuntimeException {

    /**
     * The ID that was not found. Stored so it can be referenced in
     * error messages or logging without parsing the exception message.
     */
    private final Long taskId;

    /**
     * Constructs the exception with a message that includes the missing ID.
     *
     * Calling super() with a clear, descriptive message ensures that
     * log entries contain useful information. Always prefer specific messages
     * over generic ones like "not found" or "error".
     *
     * Example message: "Task not found with id: 42"
     */
    public TaskNotFoundException(Long id) {
        super("Task not found with id: " + id);
        this.taskId = id;
    }

    public Long getTaskId() {
        return taskId;
    }
}
