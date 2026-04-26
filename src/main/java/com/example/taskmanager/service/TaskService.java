package com.example.taskmanager.service;

import com.example.taskmanager.dto.TaskCreateRequest;
import com.example.taskmanager.dto.TaskResponse;
import com.example.taskmanager.dto.TaskUpdateRequest;
import com.example.taskmanager.entity.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * TaskService — Service Layer Interface
 * ======================================
 * This interface defines the CONTRACT for the task business logic layer.
 * Controllers depend on this interface, not on the concrete implementation.
 *
 * WHY DEFINE AN INTERFACE RATHER THAN USING THE IMPLEMENTATION DIRECTLY?
 * ------------------------------------------------------------------------
 * 1. DEPENDENCY INVERSION PRINCIPLE (DIP — the "D" in SOLID):
 *    High-level modules (controllers) should depend on abstractions
 *    (interfaces), not concretions (implementation classes). This means:
 *    - Controllers are decoupled from HOW tasks are managed
 *    - You can swap the implementation without changing the controller
 *
 * 2. TESTABILITY:
 *    In @WebMvcTest (controller tests), we mock this interface with Mockito:
 *      @MockBean TaskService taskService;
 *    Without an interface, mocking is still possible but requires more
 *    configuration and Mockito must subclass the concrete class.
 *
 * 3. MULTIPLE IMPLEMENTATIONS (theoretical):
 *    You could have TaskServiceImpl (real JPA) and TaskServiceCacheImpl
 *    (adds Redis caching) — both implement this interface. Spring would
 *    inject the appropriate one based on configuration.
 *
 * LAYER RESPONSIBILITIES:
 *   Controller layer — HTTP concerns (parse request, build response, routing)
 *   Service layer    — Business logic (validation, orchestration, transactions)
 *   Repository layer — Data access (SQL queries, JPA operations)
 *
 * Notice that this service operates on DTOs, not entities. The service layer
 * is responsible for the translation (via the mapper). Controllers never see
 * entities; repositories never see DTOs. This is clean architecture layering.
 */
public interface TaskService {

    /**
     * Create a new task from the given request.
     * The new task will have status TODO and server-assigned id/timestamps.
     *
     * @param request validated create request DTO
     * @return the created task as a response DTO (includes the server-assigned id)
     */
    TaskResponse createTask(TaskCreateRequest request);

    /**
     * Retrieve a single task by its primary key.
     *
     * @param id the task's primary key
     * @return the task as a response DTO
     * @throws com.example.taskmanager.exception.TaskNotFoundException if no task with this id exists
     */
    TaskResponse getTaskById(Long id);

    /**
     * Retrieve all tasks without pagination.
     * Use this only for small datasets. Prefer getAllTasksPaged() in production
     * where the task count can grow unbounded.
     *
     * @return all tasks as a list of response DTOs
     */
    List<TaskResponse> getAllTasks();

    /**
     * Retrieve a page of tasks, sorted and sized by the Pageable parameter.
     *
     * @param pageable pagination/sort specification (page, size, sort)
     * @return a Page wrapping the task DTOs with pagination metadata
     */
    Page<TaskResponse> getAllTasksPaged(Pageable pageable);

    /**
     * Retrieve all tasks with a specific status.
     *
     * @param status the status to filter by
     * @return tasks matching the given status, as a list of response DTOs
     */
    List<TaskResponse> getTasksByStatus(TaskStatus status);

    /**
     * Replace an existing task's data with the values in the request.
     * Implements PUT semantics — the full task is replaced (not partially patched).
     *
     * @param id      the primary key of the task to update
     * @param request validated update request DTO
     * @return the updated task as a response DTO
     * @throws com.example.taskmanager.exception.TaskNotFoundException if no task with this id exists
     */
    TaskResponse updateTask(Long id, TaskUpdateRequest request);

    /**
     * Delete a task by its primary key.
     *
     * @param id the primary key of the task to delete
     * @throws com.example.taskmanager.exception.TaskNotFoundException if no task with this id exists
     */
    void deleteTask(Long id);
}
