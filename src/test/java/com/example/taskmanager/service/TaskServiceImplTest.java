package com.example.taskmanager.service;

import com.example.taskmanager.dto.TaskCreateRequest;
import com.example.taskmanager.dto.TaskResponse;
import com.example.taskmanager.dto.TaskUpdateRequest;
import com.example.taskmanager.entity.Task;
import com.example.taskmanager.entity.TaskStatus;
import com.example.taskmanager.exception.TaskNotFoundException;
import com.example.taskmanager.mapper.TaskMapper;
import com.example.taskmanager.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * TaskServiceImplTest — Service Layer Unit Tests
 * ================================================
 * These are PURE UNIT TESTS. No Spring context is started. No database is used.
 * Every dependency of TaskServiceImpl is replaced with a Mockito mock.
 *
 * This means:
 *   ✓ Tests run in milliseconds (no I/O, no network, no Spring startup)
 *   ✓ Tests focus exclusively on the business logic in TaskServiceImpl
 *   ✓ Database behaviour is simulated, not relied upon
 *   ✓ You control exactly what the mocked repository returns
 *
 * @ExtendWith(MockitoExtension.class):
 *   Activates Mockito's JUnit 5 extension. This:
 *   - Creates mock objects for fields annotated with @Mock
 *   - Injects those mocks into the field annotated with @InjectMocks
 *   - Verifies that all @Mock fields are actually used (strict stubbing)
 *   - No need for manual MockitoAnnotations.openMocks(this) in a @BeforeEach
 *
 * MOCKITO VOCABULARY:
 *   Mock    — a fake implementation of a class/interface. By default, all methods
 *             return "empty" values (null, 0, false, empty collections, empty Optional).
 *   Stub    — configure a mock to return a specific value when called with specific args.
 *             given(repository.findById(1L)).willReturn(Optional.of(task));
 *   Verify  — assert that a mock method was called (or not called).
 *             then(repository).should().save(any(Task.class));
 *
 * BDD (Behaviour-Driven Development) STYLE:
 *   We use BDDMockito (given/when/then) instead of traditional Mockito (when/thenReturn).
 *   BDDMockito is the same library — it just renames methods to match the BDD vocabulary:
 *     given(...).willReturn(...)  instead of  when(...).thenReturn(...)
 *     then(...).should()          instead of  verify(...)
 *   The BDD style reads more naturally: "given the repo returns X, when we call service,
 *   then we expect Y".
 *
 * TEST STRUCTURE — Arrange / Act / Assert (AAA):
 *   Arrange — set up the test data and mock behaviour (the "given" phase)
 *   Act     — call the method under test (the "when" phase)
 *   Assert  — verify the result (the "then" phase)
 */
@ExtendWith(MockitoExtension.class)
class TaskServiceImplTest {

    // ── Mocks ────────────────────────────────────────────────────────────────

    /**
     * @Mock creates a Mockito mock of TaskRepository.
     * This replaces the real Spring Data JPA repository — no database needed.
     * All methods return empty defaults unless stubbed with given().
     */
    @Mock
    private TaskRepository taskRepository;

    /**
     * @Mock creates a Mockito mock of TaskMapper.
     * We mock the mapper so that mapping failures don't mask service logic bugs.
     * In most tests, we stub the mapper to return predictable test DTOs/entities.
     */
    @Mock
    private TaskMapper taskMapper;

    /**
     * @InjectMocks creates an instance of TaskServiceImpl and injects the
     * mocks above via constructor injection (Mockito detects the constructor
     * and passes the mocks). The result is a real TaskServiceImpl running
     * against fake dependencies.
     */
    @InjectMocks
    private TaskServiceImpl taskService;

    // ── Test Fixtures ─────────────────────────────────────────────────────────
    // Shared test data set up before each test via @BeforeEach.

    private Task sampleTask;
    private TaskResponse sampleResponse;

    /**
     * @BeforeEach — runs before every @Test method.
     * We rebuild the fixtures fresh for each test to avoid state leakage between tests.
     * (Mockito mocks are also reset between tests by MockitoExtension.)
     */
    @BeforeEach
    void setUp() {
        sampleTask = new Task("Write unit tests", "Cover the service layer", TaskStatus.TODO);
        // Simulate the ID that the database would assign after save()
        // We use reflection-free approach: just build the response DTO directly
        sampleResponse = new TaskResponse(
                1L,
                "Write unit tests",
                "Cover the service layer",
                TaskStatus.TODO,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    // =========================================================================
    // CREATE TESTS
    // =========================================================================

    @Test
    @DisplayName("createTask: valid request → saves entity and returns response")
    void createTask_validRequest_savesAndReturnsResponse() {
        // ── Arrange ───────────────────────────────────────────────────────────
        TaskCreateRequest request = new TaskCreateRequest("Write unit tests", "Cover the service layer");

        // Stub mapper.toEntity() to return our sample task
        given(taskMapper.toEntity(request)).willReturn(sampleTask);
        // Stub repository.save() to return our sample task (simulates DB assign of ID)
        given(taskRepository.save(sampleTask)).willReturn(sampleTask);
        // Stub mapper.toResponse() to return our sample response DTO
        given(taskMapper.toResponse(sampleTask)).willReturn(sampleResponse);

        // ── Act ───────────────────────────────────────────────────────────────
        TaskResponse result = taskService.createTask(request);

        // ── Assert ────────────────────────────────────────────────────────────
        assertThat(result).isNotNull();
        assertThat(result.title()).isEqualTo("Write unit tests");
        assertThat(result.status()).isEqualTo(TaskStatus.TODO);

        // Verify the service called the repository's save() exactly once
        then(taskRepository).should().save(sampleTask);
        // Verify the task status was set to TODO (the business rule)
        assertThat(sampleTask.getStatus()).isEqualTo(TaskStatus.TODO);
    }

    // =========================================================================
    // READ TESTS
    // =========================================================================

    @Test
    @DisplayName("getTaskById: existing ID → returns response DTO")
    void getTaskById_existingId_returnsResponse() {
        // ── Arrange ───────────────────────────────────────────────────────────
        given(taskRepository.findById(1L)).willReturn(Optional.of(sampleTask));
        given(taskMapper.toResponse(sampleTask)).willReturn(sampleResponse);

        // ── Act ───────────────────────────────────────────────────────────────
        TaskResponse result = taskService.getTaskById(1L);

        // ── Assert ────────────────────────────────────────────────────────────
        assertThat(result).isEqualTo(sampleResponse);
        // Verify findById was called with the correct ID
        then(taskRepository).should().findById(1L);
    }

    @Test
    @DisplayName("getTaskById: non-existent ID → throws TaskNotFoundException")
    void getTaskById_nonExistentId_throwsTaskNotFoundException() {
        // ── Arrange ───────────────────────────────────────────────────────────
        // Repository returns empty Optional — simulates "not found"
        given(taskRepository.findById(99L)).willReturn(Optional.empty());

        // ── Act + Assert ──────────────────────────────────────────────────────
        // assertThatThrownBy: AssertJ's way to assert that a specific exception is thrown.
        // More readable than try/catch or @Test(expected = ...).
        assertThatThrownBy(() -> taskService.getTaskById(99L))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessageContaining("99");  // message should include the missing ID

        // Verify the mapper was NOT called (no entity to map)
        then(taskMapper).should(never()).toResponse(any());
    }

    @Test
    @DisplayName("getAllTasks: returns all tasks mapped to DTOs")
    void getAllTasks_returnsAllTasksMapped() {
        // ── Arrange ───────────────────────────────────────────────────────────
        List<Task> taskList = List.of(sampleTask);
        List<TaskResponse> responseList = List.of(sampleResponse);

        given(taskRepository.findAll()).willReturn(taskList);
        given(taskMapper.toResponseList(taskList)).willReturn(responseList);

        // ── Act ───────────────────────────────────────────────────────────────
        List<TaskResponse> result = taskService.getAllTasks();

        // ── Assert ────────────────────────────────────────────────────────────
        assertThat(result).hasSize(1);
        assertThat(result).containsExactly(sampleResponse);
    }

    @Test
    @DisplayName("getTasksByStatus: filters by status correctly")
    void getTasksByStatus_returnsMatchingTasks() {
        // ── Arrange ───────────────────────────────────────────────────────────
        List<Task> todoTasks = List.of(sampleTask);
        List<TaskResponse> todoResponses = List.of(sampleResponse);

        given(taskRepository.findByStatus(TaskStatus.TODO)).willReturn(todoTasks);
        given(taskMapper.toResponseList(todoTasks)).willReturn(todoResponses);

        // ── Act ───────────────────────────────────────────────────────────────
        List<TaskResponse> result = taskService.getTasksByStatus(TaskStatus.TODO);

        // ── Assert ────────────────────────────────────────────────────────────
        assertThat(result).hasSize(1);
        then(taskRepository).should().findByStatus(TaskStatus.TODO);
        // Verify the OTHER status was NOT queried
        then(taskRepository).should(never()).findByStatus(TaskStatus.DONE);
    }

    // =========================================================================
    // UPDATE TESTS
    // =========================================================================

    @Test
    @DisplayName("updateTask: existing ID → applies update and returns updated response")
    void updateTask_existingId_appliesUpdateAndReturnsResponse() {
        // ── Arrange ───────────────────────────────────────────────────────────
        TaskUpdateRequest request = new TaskUpdateRequest(
                "Updated title", "Updated description", TaskStatus.IN_PROGRESS);

        TaskResponse updatedResponse = new TaskResponse(
                1L, "Updated title", "Updated description",
                TaskStatus.IN_PROGRESS, LocalDateTime.now(), LocalDateTime.now());

        given(taskRepository.findById(1L)).willReturn(Optional.of(sampleTask));
        // updateEntityFromRequest modifies sampleTask in place — no return value (void)
        // Mockito stubs void methods with doNothing() by default — no stub needed
        given(taskMapper.toResponse(sampleTask)).willReturn(updatedResponse);

        // ── Act ───────────────────────────────────────────────────────────────
        TaskResponse result = taskService.updateTask(1L, request);

        // ── Assert ────────────────────────────────────────────────────────────
        assertThat(result.title()).isEqualTo("Updated title");
        assertThat(result.status()).isEqualTo(TaskStatus.IN_PROGRESS);

        // Verify the mapper was asked to update the entity from the request
        then(taskMapper).should().updateEntityFromRequest(eq(request), eq(sampleTask));
    }

    @Test
    @DisplayName("updateTask: non-existent ID → throws TaskNotFoundException")
    void updateTask_nonExistentId_throwsTaskNotFoundException() {
        // ── Arrange ───────────────────────────────────────────────────────────
        TaskUpdateRequest request = new TaskUpdateRequest("Title", null, TaskStatus.DONE);
        given(taskRepository.findById(99L)).willReturn(Optional.empty());

        // ── Act + Assert ──────────────────────────────────────────────────────
        assertThatThrownBy(() -> taskService.updateTask(99L, request))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessageContaining("99");

        // The mapper should never be called if the task doesn't exist
        then(taskMapper).should(never()).updateEntityFromRequest(any(), any());
    }

    // =========================================================================
    // DELETE TESTS
    // =========================================================================

    @Test
    @DisplayName("deleteTask: existing ID → calls repository.delete()")
    void deleteTask_existingId_callsRepositoryDelete() {
        // ── Arrange ───────────────────────────────────────────────────────────
        given(taskRepository.findById(1L)).willReturn(Optional.of(sampleTask));

        // ── Act ───────────────────────────────────────────────────────────────
        taskService.deleteTask(1L);

        // ── Assert ────────────────────────────────────────────────────────────
        // Verify delete() was called with the loaded entity (not deleteById)
        then(taskRepository).should().delete(sampleTask);
    }

    @Test
    @DisplayName("deleteTask: non-existent ID → throws TaskNotFoundException, does not call delete()")
    void deleteTask_nonExistentId_throwsNotFoundException_doesNotCallDelete() {
        // ── Arrange ───────────────────────────────────────────────────────────
        given(taskRepository.findById(99L)).willReturn(Optional.empty());

        // ── Act + Assert ──────────────────────────────────────────────────────
        assertThatThrownBy(() -> taskService.deleteTask(99L))
                .isInstanceOf(TaskNotFoundException.class);

        // Critical: verify delete() was NEVER called
        then(taskRepository).should(never()).delete(any(Task.class));
    }
}
