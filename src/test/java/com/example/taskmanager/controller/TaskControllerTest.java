package com.example.taskmanager.controller;

import com.example.taskmanager.config.SecurityConfig;
import com.example.taskmanager.dto.TaskCreateRequest;
import com.example.taskmanager.dto.TaskResponse;
import com.example.taskmanager.dto.TaskUpdateRequest;
import com.example.taskmanager.entity.TaskStatus;
import com.example.taskmanager.exception.GlobalExceptionHandler;
import com.example.taskmanager.exception.TaskNotFoundException;
import com.example.taskmanager.service.TaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TaskControllerTest — Web Layer (MVC) Tests
 * ============================================
 * These tests verify HTTP request handling: routing, deserialization,
 * validation, and response serialization. They do NOT test business logic
 * (that belongs in TaskServiceImplTest).
 *
 * @WebMvcTest(TaskController.class):
 *   Starts ONLY the Spring MVC layer — no JPA, no actual database.
 *   Loads:
 *     - DispatcherServlet and related MVC infrastructure
 *     - TaskController (the class under test)
 *     - GlobalExceptionHandler (via @Import below)
 *     - Jackson for JSON serialization
 *     - MockMvc for making HTTP requests without a real server
 *   Does NOT load:
 *     - TaskServiceImpl (replaced by @MockBean)
 *     - TaskRepository
 *     - DataSource / JPA / H2
 *
 *   This makes @WebMvcTest tests significantly faster than @SpringBootTest,
 *   typically running in under a second.
 *
 * @Import({SecurityConfig.class, GlobalExceptionHandler.class}):
 *   @WebMvcTest doesn't automatically include @Configuration classes outside
 *   the controller's package. We import SecurityConfig so our permissive
 *   dev security rules apply (otherwise Spring Security blocks everything).
 *   We import GlobalExceptionHandler so our custom error responses are tested.
 *
 * MockMvc:
 *   A Spring Test utility that simulates HTTP requests against your controllers
 *   without starting a real HTTP server. It's faster than TestRestTemplate
 *   (which requires a running server) and gives you fine-grained assertions
 *   on the response status, headers, and body.
 *
 * @MockBean:
 *   Creates a Mockito mock and registers it as a Spring bean in the test
 *   application context. The controller's constructor receives this mock
 *   instead of a real TaskServiceImpl. We then stub the mock's methods
 *   to control what the controller "sees" from the service layer.
 *
 * @WithMockUser:
 *   Since our SecurityConfig currently uses anyRequest().permitAll(), this
 *   annotation is technically optional. However, including it is a good
 *   habit: when you switch to .anyRequest().authenticated(), your tests
 *   will continue to work. It simulates an authenticated user without
 *   a real OAuth2 token or HTTP Basic credentials.
 *
 * jsonPath("$.fieldName"):
 *   Uses Jayway JsonPath to navigate the JSON response body.
 *   $.id     — root-level field "id"
 *   $.content[0].title — first element of an array field "content"
 *   $.fieldErrors.title — nested field in an object
 *   Matchers come from Hamcrest: is(), hasSize(), notNullValue(), etc.
 */
@WebMvcTest(TaskController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class TaskControllerTest {

    /**
     * @Autowired MockMvc — injected by @WebMvcTest automatically.
     * Used to perform HTTP requests and assert on responses.
     */
    @Autowired
    private MockMvc mockMvc;

    /**
     * @Autowired ObjectMapper — Jackson's JSON serializer/deserializer.
     * Used to convert our request DTOs to JSON strings for the request body.
     * The same ObjectMapper that Spring Boot configures for the application
     * (respects application.yml Jackson settings like write-dates-as-timestamps).
     */
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * @MockBean — a Mockito mock registered as a Spring bean.
     * The controller will receive this mock via constructor injection.
     */
    @MockBean
    private TaskService taskService;

    // ── Test Fixtures ─────────────────────────────────────────────────────────

    private TaskResponse sampleResponse;

    @BeforeEach
    void setUp() {
        sampleResponse = new TaskResponse(
                1L,
                "Write unit tests",
                "Cover the service layer with Mockito",
                TaskStatus.TODO,
                LocalDateTime.of(2024, 1, 15, 10, 30, 0),
                LocalDateTime.of(2024, 1, 15, 10, 30, 0)
        );
    }

    // =========================================================================
    // POST /api/v1/tasks — Create Task
    // =========================================================================

    @Test
    @WithMockUser
    @DisplayName("POST /tasks: valid request → 201 Created with Location header and body")
    void createTask_validRequest_returns201WithLocationAndBody() throws Exception {
        // ── Arrange ───────────────────────────────────────────────────────────
        TaskCreateRequest request = new TaskCreateRequest("Write unit tests", "Cover the service layer");
        given(taskService.createTask(any(TaskCreateRequest.class))).willReturn(sampleResponse);

        // ── Act + Assert ──────────────────────────────────────────────────────
        mockMvc.perform(
                post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        // objectMapper.writeValueAsString converts the DTO to JSON:
                        // {"title":"Write unit tests","description":"Cover the service layer"}
                        .content(objectMapper.writeValueAsString(request))
                )
                // Assert HTTP 201 status
                .andExpect(status().isCreated())
                // Assert Location header is present (e.g., http://localhost/api/v1/tasks/1)
                .andExpect(header().exists("Location"))
                // Assert response body JSON fields
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.title", is("Write unit tests")))
                .andExpect(jsonPath("$.status", is("TODO")));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /tasks: blank title → 400 Bad Request with field error")
    void createTask_blankTitle_returns400WithFieldError() throws Exception {
        // ── Arrange ───────────────────────────────────────────────────────────
        // Empty title violates @NotBlank
        TaskCreateRequest invalidRequest = new TaskCreateRequest("", null);

        // ── Act + Assert ──────────────────────────────────────────────────────
        mockMvc.perform(
                post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest))
                )
                .andExpect(status().isBadRequest())
                // Our GlobalExceptionHandler returns fieldErrors map
                .andExpect(jsonPath("$.fieldErrors.title", notNullValue()))
                // The service should never be called for invalid requests
                // (validation happens before the controller method body executes)
                ;
        // Verify service was NOT called (Mockito strict mode will catch this anyway)
    }

    @Test
    @WithMockUser
    @DisplayName("POST /tasks: missing content-type → 415 Unsupported Media Type")
    void createTask_noContentType_returns415() throws Exception {
        // Jackson cannot parse the body without knowing it's JSON.
        // Spring returns 415 before the controller method is invoked.
        mockMvc.perform(
                post("/api/v1/tasks")
                        // No .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"test\"}")
                )
                .andExpect(status().isUnsupportedMediaType());
    }

    // =========================================================================
    // GET /api/v1/tasks/{id} — Get Task by ID
    // =========================================================================

    @Test
    @WithMockUser
    @DisplayName("GET /tasks/{id}: existing ID → 200 OK with task body")
    void getTaskById_existingId_returns200WithBody() throws Exception {
        // ── Arrange ───────────────────────────────────────────────────────────
        given(taskService.getTaskById(1L)).willReturn(sampleResponse);

        // ── Act + Assert ──────────────────────────────────────────────────────
        mockMvc.perform(get("/api/v1/tasks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.title", is("Write unit tests")))
                .andExpect(jsonPath("$.status", is("TODO")));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /tasks/{id}: non-existent ID → 404 Not Found with error body")
    void getTaskById_nonExistentId_returns404() throws Exception {
        // ── Arrange ───────────────────────────────────────────────────────────
        // Service throws TaskNotFoundException — GlobalExceptionHandler catches it
        given(taskService.getTaskById(99L)).willThrow(new TaskNotFoundException(99L));

        // ── Act + Assert ──────────────────────────────────────────────────────
        mockMvc.perform(get("/api/v1/tasks/99"))
                .andExpect(status().isNotFound())
                // Our ErrorResponse body should contain status and message
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /tasks/{id}: non-numeric ID → 400 Bad Request")
    void getTaskById_nonNumericId_returns400() throws Exception {
        // "abc" cannot be converted to Long — MethodArgumentTypeMismatchException
        mockMvc.perform(get("/api/v1/tasks/abc"))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // GET /api/v1/tasks — Get All Tasks
    // =========================================================================

    @Test
    @WithMockUser
    @DisplayName("GET /tasks: no filters → 200 OK with paginated response")
    void getAllTasks_noFilter_returns200WithPage() throws Exception {
        // ── Arrange ───────────────────────────────────────────────────────────
        // Page responses have a specific JSON structure (content, totalElements, etc.)
        // We need to return a real Page object from the mock.
        org.springframework.data.domain.Page<TaskResponse> page =
                new org.springframework.data.domain.PageImpl<>(List.of(sampleResponse));
        given(taskService.getAllTasksPaged(any())).willReturn(page);

        // ── Act + Assert ──────────────────────────────────────────────────────
        mockMvc.perform(get("/api/v1/tasks"))
                .andExpect(status().isOk())
                // Page response has "content" array and "totalElements"
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title", is("Write unit tests")))
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /tasks?status=TODO → 200 OK with filtered list")
    void getAllTasks_withStatusFilter_returnsFilteredList() throws Exception {
        // ── Arrange ───────────────────────────────────────────────────────────
        given(taskService.getTasksByStatus(TaskStatus.TODO)).willReturn(List.of(sampleResponse));

        // ── Act + Assert ──────────────────────────────────────────────────────
        mockMvc.perform(get("/api/v1/tasks").param("status", "TODO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /tasks?status=INVALID → 400 Bad Request")
    void getAllTasks_invalidStatus_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/tasks").param("status", "INVALID_STATUS"))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // PUT /api/v1/tasks/{id} — Update Task
    // =========================================================================

    @Test
    @WithMockUser
    @DisplayName("PUT /tasks/{id}: valid request → 200 OK with updated body")
    void updateTask_validRequest_returns200() throws Exception {
        // ── Arrange ───────────────────────────────────────────────────────────
        TaskUpdateRequest request = new TaskUpdateRequest(
                "Updated title", "Updated description", TaskStatus.IN_PROGRESS);
        TaskResponse updatedResponse = new TaskResponse(
                1L, "Updated title", "Updated description",
                TaskStatus.IN_PROGRESS, LocalDateTime.now(), LocalDateTime.now());

        given(taskService.updateTask(eq(1L), any(TaskUpdateRequest.class)))
                .willReturn(updatedResponse);

        // ── Act + Assert ──────────────────────────────────────────────────────
        mockMvc.perform(
                put("/api/v1/tasks/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Updated title")))
                .andExpect(jsonPath("$.status", is("IN_PROGRESS")));
    }

    @Test
    @WithMockUser
    @DisplayName("PUT /tasks/{id}: non-existent ID → 404 Not Found")
    void updateTask_nonExistentId_returns404() throws Exception {
        // ── Arrange ───────────────────────────────────────────────────────────
        TaskUpdateRequest request = new TaskUpdateRequest("Title", null, TaskStatus.DONE);
        given(taskService.updateTask(eq(99L), any(TaskUpdateRequest.class)))
                .willThrow(new TaskNotFoundException(99L));

        // ── Act + Assert ──────────────────────────────────────────────────────
        mockMvc.perform(
                put("/api/v1/tasks/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // DELETE /api/v1/tasks/{id} — Delete Task
    // =========================================================================

    @Test
    @WithMockUser
    @DisplayName("DELETE /tasks/{id}: existing ID → 204 No Content")
    void deleteTask_existingId_returns204() throws Exception {
        // ── Arrange ───────────────────────────────────────────────────────────
        // deleteTask() is void — configure mock to do nothing (the default)
        willDoNothing().given(taskService).deleteTask(1L);

        // ── Act + Assert ──────────────────────────────────────────────────────
        mockMvc.perform(delete("/api/v1/tasks/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser
    @DisplayName("DELETE /tasks/{id}: non-existent ID → 404 Not Found")
    void deleteTask_nonExistentId_returns404() throws Exception {
        // ── Arrange ───────────────────────────────────────────────────────────
        willThrow(new TaskNotFoundException(99L)).given(taskService).deleteTask(99L);

        // ── Act + Assert ──────────────────────────────────────────────────────
        mockMvc.perform(delete("/api/v1/tasks/99"))
                .andExpect(status().isNotFound());
    }
}
