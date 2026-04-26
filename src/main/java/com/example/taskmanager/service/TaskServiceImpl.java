package com.example.taskmanager.service;

import com.example.taskmanager.dto.TaskCreateRequest;
import com.example.taskmanager.dto.TaskResponse;
import com.example.taskmanager.dto.TaskUpdateRequest;
import com.example.taskmanager.entity.Task;
import com.example.taskmanager.entity.TaskStatus;
import com.example.taskmanager.exception.TaskNotFoundException;
import com.example.taskmanager.mapper.TaskMapper;
import com.example.taskmanager.repository.TaskRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * TaskServiceImpl — Business Logic Implementation
 * =================================================
 * This class is the concrete implementation of TaskService. It contains all
 * business logic for task management and orchestrates the repository and mapper.
 *
 * @Service:
 *   A specialisation of @Component. Marking this with @Service:
 *     1. Registers this class as a Spring bean (Spring will create one instance
 *        and manage its lifecycle)
 *     2. Communicates to readers that this class is in the "service layer"
 *        (business logic), distinguishing it from controllers or repositories
 *     3. Enables exception translation (similar to @Repository)
 *
 * @Transactional (class-level):
 *   Applied at the class level, this means ALL public methods in this class
 *   will run within a database transaction. If the method completes normally,
 *   the transaction commits. If a RuntimeException is thrown, it rolls back.
 *
 *   KEY CONCEPTS:
 *   - Transaction = a unit of work that is atomic (all-or-nothing)
 *   - Spring creates a database transaction before your method runs and either
 *     commits (success) or rolls back (exception) after
 *   - Hibernate's "session" (which tracks entity changes) is bound to the
 *     transaction. Entities loaded within a transaction are "managed" — Hibernate
 *     automatically detects changes to them ("dirty checking") and issues UPDATEs
 *     at the end of the transaction
 *
 *   Read-only methods could use @Transactional(readOnly = true) for a performance
 *   hint to the database (prevents dirty checking overhead). For clarity in this
 *   educational example, we apply it at the class level uniformly.
 *
 * CONSTRUCTOR INJECTION (vs. Field Injection):
 *   We use constructor injection:
 *     private final TaskRepository repository;
 *     public TaskServiceImpl(TaskRepository repository, TaskMapper mapper) { ... }
 *
 *   NOT field injection (@Autowired on the field). Why?
 *     1. Immutability: final fields cannot be reassigned after construction
 *     2. Testability: you can instantiate this class in a test without Spring:
 *        new TaskServiceImpl(mockRepository, mockMapper)
 *     3. Explicitness: dependencies are visible in the constructor signature
 *     4. Null safety: if a bean is missing, the application fails at startup,
 *        not at runtime when the field is first accessed
 *
 *   Spring automatically detects a single constructor and injects its parameters,
 *   no @Autowired annotation needed.
 */
@Service
@Transactional
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final TaskMapper taskMapper;

    // Spring injects TaskRepository and TaskMapper beans automatically.
    // Both are singletons — one instance shared across all service invocations.
    public TaskServiceImpl(TaskRepository taskRepository, TaskMapper taskMapper) {
        this.taskRepository = taskRepository;
        this.taskMapper = taskMapper;
    }

    // =========================================================================
    // CREATE
    // =========================================================================

    /**
     * Create a new task.
     *
     * FLOW:
     *   1. Map the request DTO to a Task entity (via MapStruct)
     *   2. Set the initial status to TODO (business rule: not client-controlled)
     *   3. Save the entity — JPA assigns the ID and @PrePersist sets timestamps
     *   4. Map the saved entity back to a response DTO and return it
     *
     * WHY RETURN A RESPONSE DTO INSTEAD OF VOID?
     *   The controller needs the server-assigned ID to build the Location header
     *   (HTTP 201 responses should include Location: /api/v1/tasks/{newId}).
     *   Returning the full DTO is also useful for clients that want to confirm
     *   what was created without issuing a follow-up GET.
     */
    @Override
    public TaskResponse createTask(TaskCreateRequest request) {
        Task task = taskMapper.toEntity(request);
        task.setStatus(TaskStatus.TODO); // Business rule: all new tasks start as TODO
        Task saved = taskRepository.save(task);
        return taskMapper.toResponse(saved);
    }

    // =========================================================================
    // READ
    // =========================================================================

    /**
     * Retrieve a task by ID.
     *
     * repository.findById() returns Optional<Task> — a container that either
     * holds a Task (found) or is empty (not found).
     *
     * .orElseThrow() extracts the Task if present, or calls the lambda to
     * produce an exception if empty. This is safer than .get() which throws
     * NoSuchElementException (an unhelpful error with no context).
     *
     * @Transactional(readOnly = true) could be added to read operations for
     * a slight performance improvement (skips dirty checking). Omitted here
     * for simplicity, as the class-level @Transactional covers it.
     */
    @Override
    public TaskResponse getTaskById(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
        return taskMapper.toResponse(task);
    }

    /**
     * Retrieve all tasks — no pagination.
     *
     * findAll() issues SELECT * FROM tasks, which grows unboundedly.
     * Fine for development and small datasets; use getAllTasksPaged() for
     * any dataset that might exceed hundreds of rows.
     *
     * taskMapper.toResponseList() maps the entire List<Task> in one call,
     * which MapStruct implements as a simple for-loop internally.
     */
    @Override
    public List<TaskResponse> getAllTasks() {
        List<Task> tasks = taskRepository.findAll();
        return taskMapper.toResponseList(tasks);
    }

    /**
     * Retrieve a page of tasks.
     *
     * Page<Task>.map() is a convenience method that applies a function to
     * each element and returns Page<TaskResponse>. This preserves all
     * pagination metadata (totalElements, totalPages, etc.) while
     * transforming the content.
     */
    @Override
    public Page<TaskResponse> getAllTasksPaged(Pageable pageable) {
        return taskRepository.findAll(pageable)
                .map(taskMapper::toResponse);
        // taskMapper::toResponse is a method reference — equivalent to:
        // .map(task -> taskMapper.toResponse(task))
    }

    /**
     * Retrieve all tasks with a specific status.
     */
    @Override
    public List<TaskResponse> getTasksByStatus(TaskStatus status) {
        List<Task> tasks = taskRepository.findByStatus(status);
        return taskMapper.toResponseList(tasks);
    }

    // =========================================================================
    // UPDATE
    // =========================================================================

    /**
     * Replace an existing task with new data (PUT semantics).
     *
     * FLOW:
     *   1. Load the existing entity from the database (throws 404 if not found)
     *   2. Apply changes from the request DTO to the entity in place (via MapStruct)
     *   3. The transaction commits at method end — Hibernate's dirty checking
     *      detects the changed fields and issues an UPDATE automatically.
     *      No explicit repository.save() call is needed for managed entities!
     *   4. Map the updated entity to a response DTO and return
     *
     * WHY NO repository.save()?
     *   Within a transaction, an entity returned by findById() is "managed"
     *   by the JPA persistence context. Hibernate tracks all changes to it.
     *   When the transaction commits, Hibernate compares the current state to
     *   the snapshot taken at load time and auto-generates the UPDATE SQL.
     *
     *   Calling repository.save() on a managed entity IS safe (it's a no-op
     *   for managed entities in most cases) and you may prefer it for
     *   explicitness. Both styles are correct.
     */
    @Override
    public TaskResponse updateTask(Long id, TaskUpdateRequest request) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));

        taskMapper.updateEntityFromRequest(request, task);
        // task is now modified but not yet saved — the transaction commits at method end

        return taskMapper.toResponse(task);
    }

    // =========================================================================
    // DELETE
    // =========================================================================

    /**
     * Delete a task by ID.
     *
     * WHY LOAD THE ENTITY BEFORE DELETING?
     *   repository.deleteById(id) silently does nothing if the ID doesn't exist.
     *   We want to return a 404 if the caller tries to delete a non-existent task.
     *   By loading first, we get the TaskNotFoundException if needed.
     *
     *   ALTERNATIVE: use existsById(id) and throw if false, then deleteById(id).
     *   That issues two SQL queries. Loading then deleting also issues two queries
     *   but gives you the entity in case you need to do pre-deletion work
     *   (e.g., send a notification, cascade to other systems).
     */
    @Override
    public void deleteTask(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
        taskRepository.delete(task);
    }
}
