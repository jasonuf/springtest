package com.example.taskmanager.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Task — JPA Entity
 * ==================
 * This class is the "domain model" — a Java representation of a row in the
 * "tasks" database table. JPA (Jakarta Persistence API) bridges Java objects
 * and relational database tables through annotations.
 *
 * KEY PRINCIPLE — Entities vs DTOs:
 *   Entities represent your database rows and contain persistence logic.
 *   DTOs (Data Transfer Objects) represent what you send/receive over HTTP.
 *   They should be separate classes. Never expose your JPA entity directly in
 *   your REST API — it couples your database schema to your API contract and
 *   risks accidentally exposing internal fields or triggering lazy-loading issues.
 *
 * @Entity  — Marks this class as a JPA entity. Hibernate will manage instances
 *             of this class, mapping them to/from database rows.
 * @Table   — Optional; explicitly names the database table. Without it, JPA
 *             would use the class name ("Task") as the table name. Being explicit
 *             avoids surprises when class names change.
 */
@Entity
@Table(name = "tasks")
public class Task {

    /**
     * Primary Key
     * ============
     * @Id — designates this field as the primary key column.
     * @GeneratedValue — tells JPA to auto-generate the ID value.
     *
     * GenerationType.IDENTITY: delegates ID generation to the database's
     * auto-increment / serial column (BIGSERIAL in PostgreSQL, AUTO_INCREMENT
     * in MySQL, IDENTITY in H2). The database assigns the ID on INSERT, and
     * JPA reads it back. This is the most portable and efficient strategy for
     * single-node applications.
     *
     * Other strategies:
     *   SEQUENCE — uses a DB sequence object (preferred for batch inserts)
     *   UUID     — generates a UUID in Java (good for distributed systems
     *               where multiple nodes generate IDs independently)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Title — required, max 255 characters
     *
     * @Column(nullable = false) — generates a NOT NULL constraint in the DDL.
     *   If you try to save a Task with a null title, Hibernate will throw
     *   a constraint violation. However, we also validate this in the DTO layer
     *   (with @NotBlank) so the error is caught before we even attempt to persist.
     *
     * length = 255 — sets the varchar column size. H2 and PostgreSQL allow
     *   longer strings but 255 is a safe default for a title field. For longer
     *   text, use columnDefinition = "TEXT" (see description below).
     */
    @Column(nullable = false, length = 255)
    private String title;

    /**
     * Description — optional, unbounded text
     *
     * columnDefinition = "TEXT" — overrides the default varchar type with
     * TEXT, which has no character limit. Use this for user-written content.
     * nullable = true (the default) means this column can store NULL.
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Status — the workflow state of the task
     *
     * @Enumerated(EnumType.STRING) — store the enum constant's name ("TODO",
     * "IN_PROGRESS", "DONE") as a varchar in the database. See TaskStatus.java
     * for why STRING is preferred over ORDINAL.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status;

    /**
     * Audit Timestamps
     * =================
     * createdAt records when the row was first inserted.
     * updatedAt records when the row was last modified.
     *
     * updatable = false on createdAt means Hibernate will never include this
     * column in an UPDATE statement — it's set once and never changed.
     *
     * These are set automatically by the JPA lifecycle callbacks below
     * (@PrePersist and @PreUpdate). This means application code never needs
     * to set these fields manually — the persistence layer handles them.
     *
     * ALTERNATIVE: Spring Data JPA's @CreatedDate and @LastModifiedDate
     * annotations (with @EnableJpaAuditing) do the same thing with less
     * boilerplate. The manual @PrePersist approach is shown here to make
     * the mechanism explicit.
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // =========================================================================
    // JPA LIFECYCLE CALLBACKS
    // =========================================================================

    /**
     * @PrePersist — JPA calls this method immediately before an INSERT.
     * We use it to initialise both timestamp fields and the default status.
     *
     * WHY NOT SET THESE IN THE CONSTRUCTOR?
     * JPA requires a no-argument constructor (see below). Setting defaults
     * in @PrePersist guarantees they are set regardless of which constructor
     * was used to create the object.
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        // Safety net: ensure status is never null in the database.
        // TaskServiceImpl also sets this explicitly, but defensive defaults
        // are good practice at the entity level.
        if (this.status == null) {
            this.status = TaskStatus.TODO;
        }
    }

    /**
     * @PreUpdate — JPA calls this method immediately before an UPDATE.
     * We only need to refresh updatedAt here; createdAt must not change.
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // =========================================================================
    // CONSTRUCTORS
    // =========================================================================

    /**
     * No-args constructor — REQUIRED by JPA specification.
     * Hibernate creates entity instances by calling this constructor
     * reflectively when reading rows from the database. Without it,
     * Hibernate cannot instantiate your entity.
     * Protected visibility is sufficient — JPA can access it.
     */
    protected Task() {}

    /**
     * Convenience constructor for creating new tasks programmatically.
     * The id and timestamps are intentionally omitted — they are managed
     * by JPA, not by the caller.
     */
    public Task(String title, String description, TaskStatus status) {
        this.title = title;
        this.description = description;
        this.status = status;
    }

    // =========================================================================
    // GETTERS AND SETTERS
    // =========================================================================
    //
    // WHY MANUAL GETTERS/SETTERS INSTEAD OF LOMBOK @Data?
    // This is educational code — we write them out explicitly so readers see
    // exactly what is generated. In a production codebase, @Data or @Getter/@Setter
    // from Lombok eliminates this boilerplate. Both approaches are valid.
    //
    // NOTE: There is no setId() method. IDs are assigned by the database and
    // should never be changed by application code.

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "Task{id=" + id + ", title='" + title + "', status=" + status + "}";
    }
}
