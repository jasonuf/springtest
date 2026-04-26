package com.example.taskmanager.mapper;

import com.example.taskmanager.dto.TaskCreateRequest;
import com.example.taskmanager.dto.TaskResponse;
import com.example.taskmanager.dto.TaskUpdateRequest;
import com.example.taskmanager.entity.Task;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

/**
 * TaskMapper — MapStruct DTO Mapper
 * ==================================
 * This interface declares the mapping contract between our DTOs and entity.
 * At compile time, MapStruct's annotation processor reads this interface and
 * generates a concrete implementation class (TaskMapperImpl.java) in the
 * target/generated-sources/annotations/ directory.
 *
 * You can inspect the generated file after running `mvn compile` to understand
 * exactly what MapStruct generates — it's plain, readable Java with no reflection.
 *
 * WHY MAPSTRUCT?
 * ---------------
 * Alternative 1 — Manual mapping (lots of boilerplate):
 *   TaskResponse toResponse(Task task) {
 *     return new TaskResponse(task.getId(), task.getTitle(), ...);
 *   }
 *
 * Alternative 2 — ModelMapper (popular but reflection-based):
 *   - Resolves mappings at runtime → errors at runtime, not compile time
 *   - Slower due to reflection
 *   - Harder to debug (magic)
 *
 * MapStruct generates alternative 1 FOR you, giving you:
 *   ✓ Compile-time error checking (mismatched types are caught at build time)
 *   ✓ No runtime reflection overhead
 *   ✓ Debuggable generated code
 *   ✓ Native Spring integration via componentModel = "spring"
 *
 * @Mapper(componentModel = "spring"):
 *   Tells MapStruct to annotate the generated implementation class with @Component,
 *   making it a Spring bean that can be injected with @Autowired or constructor
 *   injection. Without this, you'd have to call Mappers.getMapper(TaskMapper.class)
 *   manually (which doesn't work well with Spring's dependency injection).
 */
@Mapper(componentModel = "spring")
public interface TaskMapper {

    /**
     * Convert a Task entity to a TaskResponse DTO.
     *
     * MapStruct matches fields by name. Since Task and TaskResponse have
     * the same field names (id, title, description, status, createdAt, updatedAt),
     * no explicit @Mapping annotations are needed — MapStruct handles it automatically.
     *
     * For a Java record target, MapStruct calls the canonical constructor.
     * The generated code looks roughly like:
     *
     *   return new TaskResponse(
     *       task.getId(),
     *       task.getTitle(),
     *       task.getDescription(),
     *       task.getStatus(),
     *       task.getCreatedAt(),
     *       task.getUpdatedAt()
     *   );
     */
    TaskResponse toResponse(Task task);

    /**
     * Convert a list of Task entities to a list of TaskResponse DTOs.
     * MapStruct auto-generates this by applying toResponse() to each element.
     * No implementation needed — just the declaration.
     */
    List<TaskResponse> toResponseList(List<Task> tasks);

    /**
     * Convert a TaskCreateRequest DTO to a new Task entity.
     *
     * @Mapping(target = "id", ignore = true)
     *   Tells MapStruct to skip the "id" field on the target (Task).
     *   IDs are assigned by the database — we must never set them here.
     *
     * @Mapping(target = "status", ignore = true)
     *   Status is not part of the create request. TaskServiceImpl sets it
     *   to TaskStatus.TODO after this method returns.
     *
     * @Mapping(target = "createdAt", ignore = true)
     * @Mapping(target = "updatedAt", ignore = true)
     *   These are set by @PrePersist in the entity. MapStruct would emit
     *   a warning about unmapped target properties without these ignore directives.
     *
     * WITHOUT ignore = true: MapStruct would try to map from a field with the
     * same name in the source, fail to find it, and either leave the field null
     * or emit a compile-time warning/error (depending on configuration).
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Task toEntity(TaskCreateRequest request);

    /**
     * Apply changes from a TaskUpdateRequest to an EXISTING Task entity.
     *
     * @MappingTarget Task task — the existing entity to be updated in place.
     * MapStruct generates setter calls on the target object instead of
     * creating a new instance. This is the "update" pattern.
     *
     * Why update in place rather than creating a new entity?
     * Because the existing entity is already attached to the JPA persistence
     * context. If we detached it and created a new one, we'd lose change
     * tracking and need to manage the lifecycle manually.
     *
     * @BeanMapping(nullValuePropertyMappingStrategy = IGNORE)
     * If a field in the request is null, MapStruct will NOT overwrite the
     * corresponding field in the entity. This gives partial-update semantics
     * within a PUT endpoint. For a strict PUT (all fields replaced), remove this
     * annotation. For a true PATCH endpoint, use it and make all request fields
     * Optional<T> or nullable.
     *
     * @Mapping(target = "id", ignore = true)
     * @Mapping(target = "createdAt", ignore = true)
     * @Mapping(target = "updatedAt", ignore = true)
     *   These fields must never be modified during an update.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromRequest(TaskUpdateRequest request, @MappingTarget Task task);
}
