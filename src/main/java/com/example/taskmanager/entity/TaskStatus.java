package com.example.taskmanager.entity;

/**
 * TaskStatus — Domain Enumeration
 * ================================
 * Enums (short for "enumerations") are a special Java type that defines a fixed
 * set of named constants. They are preferable to plain String constants because:
 *   - The compiler enforces valid values (no typos at runtime)
 *   - IDEs provide autocomplete
 *   - Switch expressions can be exhaustiveness-checked
 *
 * PLACEMENT: This enum lives in the "entity" package because it describes a
 * concept in our domain model (what states a Task can be in). Both the JPA
 * entity and the DTOs reference it, so it must not be buried inside either.
 *
 * JPA STORAGE: We annotate the Task.status field with @Enumerated(EnumType.STRING)
 * which tells Hibernate to store "TODO", "IN_PROGRESS", or "DONE" as a varchar
 * column rather than the ordinal integers 0, 1, 2. Always prefer STRING storage:
 *   - Adding a new constant between existing ones doesn't shift ordinal values
 *   - Database rows are human-readable without a lookup table
 *   - Reordering constants doesn't corrupt existing data
 */
public enum TaskStatus {

    /**
     * The task has been created but no work has started.
     * This is the initial state assigned automatically on creation.
     */
    TODO,

    /**
     * Work on the task is actively underway.
     */
    IN_PROGRESS,

    /**
     * The task has been completed. Terminal state.
     */
    DONE
}
