package com.example.taskmanager.repository;

import com.example.taskmanager.entity.Task;
import com.example.taskmanager.entity.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * TaskRepository — Spring Data JPA Repository
 * =============================================
 * This interface extends JpaRepository, which is Spring Data's primary
 * repository abstraction. You write ZERO implementation code for this interface.
 * Spring Data generates a full implementation at runtime using JDK dynamic proxies.
 *
 * WHAT JpaRepository<Task, Long> GIVES YOU FOR FREE:
 *   save(Task entity)                   — INSERT or UPDATE
 *   saveAll(Iterable<Task> entities)    — batch INSERT or UPDATE
 *   findById(Long id)                   — SELECT by PK → Optional<Task>
 *   findAll()                           — SELECT all rows → List<Task>
 *   findAll(Pageable pageable)          — SELECT with pagination/sorting
 *   findAll(Sort sort)                  — SELECT with sorting
 *   existsById(Long id)                 — SELECT COUNT > 0
 *   count()                             — SELECT COUNT(*)
 *   delete(Task entity)                 — DELETE
 *   deleteById(Long id)                 — DELETE by PK
 *   deleteAll()                         — DELETE all (use with caution!)
 *
 * The two type parameters are:
 *   Task — the entity type this repository manages
 *   Long — the type of the primary key field (@Id)
 *
 * @Repository is technically optional here (Spring Data detects the JpaRepository
 * extension automatically), but it is good practice to include it because:
 *   1. It makes the intent explicit to readers
 *   2. It enables Spring's exception translation: JDBC SQLExceptions are wrapped
 *      in Spring's DataAccessException hierarchy, making them easier to catch
 *      without knowing which database you're using
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    /**
     * Find all tasks with a given status.
     *
     * SPRING DATA DERIVED QUERIES:
     * Spring Data parses method names and generates SQL automatically.
     * The pattern is: find + By + <FieldName> + [Condition]
     *
     * "findByStatus(TaskStatus status)" generates roughly:
     *   SELECT * FROM tasks WHERE status = ?
     *
     * Other examples of what Spring Data can derive:
     *   findByTitleAndStatus(String title, TaskStatus status)
     *     → WHERE title = ? AND status = ?
     *   findByCreatedAtAfter(LocalDateTime date)
     *     → WHERE created_at > ?
     *   countByStatus(TaskStatus status)
     *     → SELECT COUNT(*) WHERE status = ?
     *   existsByTitle(String title)
     *     → SELECT 1 WHERE title = ? (returns boolean)
     */
    List<Task> findByStatus(TaskStatus status);

    /**
     * Full-text search on the title field (case-insensitive).
     *
     * "ContainingIgnoreCase" generates: WHERE UPPER(title) LIKE UPPER('%keyword%')
     *
     * This is a simple substring search. For production full-text search,
     * consider PostgreSQL's tsvector/tsquery or Elasticsearch integration.
     */
    List<Task> findByTitleContainingIgnoreCase(String keyword);

    /**
     * Paginated access to all tasks.
     *
     * The Pageable parameter carries three pieces of information:
     *   - page number (0-based)
     *   - page size (records per page)
     *   - sort order (field name + direction)
     *
     * Spring MVC's PageableHandlerMethodArgumentResolver automatically
     * populates Pageable from HTTP query parameters:
     *   GET /api/v1/tasks?page=0&size=20&sort=createdAt,desc
     *
     * Returns Page<Task> which wraps the List<Task> results with metadata:
     *   - totalElements (total matching rows in DB)
     *   - totalPages
     *   - isFirst / isLast
     *   - hasNext / hasPrevious
     *
     * This method is already provided by JpaRepository, but declaring it
     * explicitly here makes the API visible to readers of this interface.
     */
    Page<Task> findAll(Pageable pageable);
}
