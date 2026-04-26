package com.example.taskmanager.controller;

import com.example.taskmanager.dto.TaskCreateRequest;
import com.example.taskmanager.dto.TaskResponse;
import com.example.taskmanager.dto.TaskUpdateRequest;
import com.example.taskmanager.entity.TaskStatus;
import com.example.taskmanager.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * TaskController — REST API Layer
 * =================================
 * The controller is the HTTP boundary of the application. Its ONLY responsibilities
 * are to:
 *   1. Receive and parse the HTTP request (path variables, query params, request body)
 *   2. Invoke the appropriate service method
 *   3. Build the HTTP response (status code, headers, body)
 *
 * NO BUSINESS LOGIC should live here. If you find yourself writing if-else
 * chains, date calculations, or calling multiple services — move that to a
 * service class.
 *
 * @RestController:
 *   A composed annotation that combines:
 *   - @Controller — registers this class as a Spring MVC controller bean
 *   - @ResponseBody — every method's return value is serialised to the response
 *     body (as JSON, since Jackson is on the classpath). Without @ResponseBody,
 *     Spring would try to resolve return values as view names (HTML template paths).
 *
 * @RequestMapping("/api/v1/tasks"):
 *   All endpoints in this controller are prefixed with /api/v1/tasks.
 *   Versioning in the URL (/v1/) is a common industry practice. When you
 *   need to introduce breaking changes, you add /v2/ without removing /v1/.
 *   Alternative versioning strategies: Accept header, custom header, subdomain.
 */
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    // Depends on the INTERFACE, not the implementation.
    // Spring injects TaskServiceImpl at startup because it's the only class
    // that implements TaskService. If you add a second implementation,
    // you'd use @Qualifier or @Primary to disambiguate.
    private final TaskService taskService;

    // Constructor injection — see TaskServiceImpl for the explanation of why.
    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    // =========================================================================
    // CREATE
    // =========================================================================

    /**
     * POST /api/v1/tasks — Create a new task
     *
     * @PostMapping — handles HTTP POST requests to the base URL (/api/v1/tasks)
     *
     * @RequestBody TaskCreateRequest request:
     *   Spring deserialises the HTTP request body (JSON) into a TaskCreateRequest
     *   object using Jackson. The content type must be application/json.
     *
     * @Valid:
     *   Triggers Jakarta Bean Validation on the deserialized object BEFORE
     *   the method body executes. If any constraint fails (@NotBlank, @Size),
     *   Spring throws MethodArgumentNotValidException, which our
     *   GlobalExceptionHandler catches and returns as a 400 response.
     *
     * RESPONSE:
     *   HTTP 201 Created — the standard status for successful resource creation.
     *   The Location header tells clients WHERE the new resource can be found.
     *   This is part of REST's HATEOAS (Hypermedia as the Engine of Application State)
     *   principle — responses include links to related actions.
     *
     * ResponseEntity.created(uri).body(response):
     *   ResponseEntity gives full control over the HTTP response:
     *   - .created(uri) sets status 201 and adds Location header
     *   - .body(response) serialises the TaskResponse as the JSON body
     *
     * ServletUriComponentsBuilder:
     *   Builds a URI by starting with the current request URL and appending
     *   the new resource's ID. This is context-aware: if your app runs behind
     *   a reverse proxy at /api, the URI will reflect that.
     */
    @PostMapping
    public ResponseEntity<TaskResponse> createTask(
            @Valid @RequestBody TaskCreateRequest request) {

        TaskResponse created = taskService.createTask(request);

        // Build: http://localhost:8080/api/v1/tasks/{id}
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()         // starts with the current URL
                .path("/{id}")                // appends "/{id}"
                .buildAndExpand(created.id()) // replaces {id} with the actual ID
                .toUri();

        return ResponseEntity.created(location).body(created);
    }

    // =========================================================================
    // READ — Single Item
    // =========================================================================

    /**
     * GET /api/v1/tasks/{id} — Retrieve a task by ID
     *
     * @GetMapping("/{id}") — handles GET requests with an {id} path segment.
     *
     * @PathVariable Long id:
     *   Spring extracts the {id} segment from the URL and converts it to Long.
     *   If conversion fails (e.g., /api/v1/tasks/abc), Spring throws
     *   MethodArgumentTypeMismatchException, caught by GlobalExceptionHandler → 400.
     *
     * ResponseEntity.ok(response):
     *   Equivalent to ResponseEntity.status(200).body(response).
     *   The .ok() shortcut is idiomatic for 200 responses.
     */
    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> getTaskById(@PathVariable Long id) {
        TaskResponse response = taskService.getTaskById(id);
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // READ — Collection
    // =========================================================================

    /**
     * GET /api/v1/tasks — Retrieve tasks (paginated, optional status filter)
     *
     * This single endpoint supports two use cases via query parameters:
     *   GET /api/v1/tasks                      → all tasks (paginated)
     *   GET /api/v1/tasks?status=TODO           → tasks with status TODO
     *   GET /api/v1/tasks?page=1&size=5        → second page of 5 tasks
     *   GET /api/v1/tasks?sort=title,asc       → sorted by title ascending
     *
     * @RequestParam(required = false) Optional<String> status:
     *   Optional<> here means the parameter is not required (alternative to
     *   required = false + defaultValue). Using Optional<String> instead of
     *   Optional<TaskStatus> because we parse it manually to give a better
     *   error message if the value is invalid.
     *
     * Pageable pageable:
     *   Spring MVC's PageableHandlerMethodArgumentResolver automatically
     *   populates this from the ?page=, ?size=, ?sort= query parameters.
     *
     * @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC):
     *   Specifies the defaults when the client doesn't provide pagination params.
     *   Without this, the default would be page=0, size=20, no sort.
     *
     * RETURN TYPE: Page<TaskResponse>
     *   Serialised to JSON as:
     *   {
     *     "content": [...],           // the actual tasks
     *     "totalElements": 42,
     *     "totalPages": 3,
     *     "number": 0,                // current page (0-based)
     *     "size": 20,
     *     "first": true,
     *     "last": false
     *   }
     *
     * NOTE ON DESIGN: The status filter and pagination are combined here for
     * simplicity. A larger API might separate them into dedicated endpoints
     * or use a more sophisticated filtering framework like Spring Data
     * Specifications or QueryDSL.
     */
    @GetMapping
    public ResponseEntity<?> getAllTasks(
            @RequestParam(required = false) Optional<String> status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        // If a status filter is provided, parse and delegate to the filtered query.
        if (status.isPresent()) {
            TaskStatus taskStatus;
            try {
                taskStatus = TaskStatus.valueOf(status.get().toUpperCase());
            } catch (IllegalArgumentException e) {
                // Return 400 with a helpful message instead of a cryptic error.
                // In a larger app, this validation would move to a dedicated
                // @RequestParam validator or a HandlerMethodArgumentResolver.
                return ResponseEntity.badRequest().body(
                        "Invalid status value: '" + status.get() +
                        "'. Valid values are: TODO, IN_PROGRESS, DONE"
                );
            }
            List<TaskResponse> filtered = taskService.getTasksByStatus(taskStatus);
            return ResponseEntity.ok(filtered);
        }

        // No status filter — return paginated results.
        Page<TaskResponse> page = taskService.getAllTasksPaged(pageable);
        return ResponseEntity.ok(page);
    }

    // =========================================================================
    // UPDATE
    // =========================================================================

    /**
     * PUT /api/v1/tasks/{id} — Replace an existing task
     *
     * PUT = full replacement. The client must provide ALL required fields.
     * Contrast with PATCH (partial update), which only sends changed fields.
     *
     * Combining @PathVariable (which task to update) and @RequestBody
     * (what to update it with) is the standard REST PUT pattern.
     */
    @PutMapping("/{id}")
    public ResponseEntity<TaskResponse> updateTask(
            @PathVariable Long id,
            @Valid @RequestBody TaskUpdateRequest request) {

        TaskResponse updated = taskService.updateTask(id, request);
        return ResponseEntity.ok(updated);
    }

    // =========================================================================
    // DELETE
    // =========================================================================

    /**
     * DELETE /api/v1/tasks/{id} — Delete a task
     *
     * HTTP 204 No Content — the standard response for successful DELETE.
     * No body is returned because the resource no longer exists.
     *
     * ResponseEntity<Void>:
     *   Void (capital V, the wrapper class) is used when the response has no body.
     *   ResponseEntity.noContent() sets status 204.
     *   ResponseEntity.noContent().build() is required because there's no body
     *   to pass to .body() — .build() finalises the ResponseEntity.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }
}
