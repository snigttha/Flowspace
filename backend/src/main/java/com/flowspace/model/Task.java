package com.flowspace.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Domain model for a user task.
 *
 * <p>Extends {@link WorkspaceItem} (inheritance) and provides:
 * <ul>
 *   <li>Private fields with validated setters (encapsulation)</li>
 *   <li>Validation matching the SQL schema constraints:
 *       title length between 3 and 100, valid due date, priority, and status</li>
 *   <li>{@link #validate()} required by the abstract parent</li>
 *   <li>{@link #summary()} for polymorphic global search</li>
 *   <li>{@link #markComplete()}, {@link #edit(String, String, String)}, and {@link #delete()} helpers</li>
 * </ul>
 *
 * <p>Field names match the SQL column names:
 * {@code task_id, user_id, title, due_date, priority, status, created_at}
 *
 * @author Member 6 (Workspace & Integration Lead)
 */
public class Task extends WorkspaceItem {

    public enum Priority {
        LOW, MEDIUM, HIGH;

        public static Priority fromString(String s) {
            if (s == null || s.isBlank()) return MEDIUM;
            for (Priority p : values()) {
                if (p.name().equalsIgnoreCase(s.trim())) return p;
            }
            throw new IllegalArgumentException("Unknown priority: " + s);
        }
    }

    public enum Status {
        TODO, IN_PROGRESS, DONE;

        public static Status fromString(String s) {
            if (s == null || s.isBlank()) return TODO;
            for (Status st : values()) {
                if (st.name().equalsIgnoreCase(s.trim())) return st;
            }
            throw new IllegalArgumentException("Unknown status: " + s);
        }
    }

    public static final int MIN_TITLE_LENGTH = 3;
    public static final int MAX_TITLE_LENGTH = 100;

    private String    title;
    private LocalDate dueDate;
    private Priority  priority = Priority.MEDIUM;
    private Status    status   = Status.TODO;

    /** Default constructor for DAO hydration. */
    public Task() {}

    /**
     * Constructor for creating a new task.
     */
    public Task(int userId, String title, LocalDate dueDate, Priority priority) {
        setUserId(userId);
        setTitle(title);
        setDueDate(dueDate);
        setPriority(priority != null ? priority : Priority.MEDIUM);
        setStatus(Status.TODO);
        setCreatedAt(LocalDateTime.now());
    }

    public Task(int userId, String title, LocalDate dueDate, Priority priority, Status status) {
        this(userId, title, dueDate, priority);
        setStatus(status != null ? status : Status.TODO);
    }

    // ── WorkspaceItem contract ───────────────────────────────────────────

    @Override
    public void validate() {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Task title is required.");
        }
        String trimmed = title.trim();
        if (trimmed.length() < MIN_TITLE_LENGTH || trimmed.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException(
                "Task title must be between " + MIN_TITLE_LENGTH + " and " + MAX_TITLE_LENGTH + " characters.");
        }
        if (dueDate == null) {
            throw new IllegalArgumentException("Due date is required.");
        }
        if (priority == null) {
            throw new IllegalArgumentException("Priority is required.");
        }
        if (status == null) {
            throw new IllegalArgumentException("Status is required.");
        }
    }

    @Override
    public String summary() {
        return "[Task] " + title + " — Due: " + dueDate + " [" + priority + "] (" + status + ")";
    }

    // ── Business methods ─────────────────────────────────────────────────

    public void markComplete() {
        this.status = Status.DONE;
    }

    public void edit(String newTitle, LocalDate newDueDate, Priority newPriority) {
        setTitle(newTitle);
        setDueDate(newDueDate);
        if (newPriority != null) {
            this.priority = newPriority;
        }
        validate();
    }

    public void delete() {
        // Soft delete hook; physical deletion executed by DAO
    }

    public boolean isOverdue() {
        return status != Status.DONE && dueDate != null && dueDate.isBefore(LocalDate.now());
    }

    // ── Getters / validated setters ──────────────────────────────────────

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Task title is required.");
        }
        String trimmed = title.trim();
        if (trimmed.length() < MIN_TITLE_LENGTH || trimmed.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException(
                "Task title must be between " + MIN_TITLE_LENGTH + " and " + MAX_TITLE_LENGTH + " characters.");
        }
        this.title = trimmed;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        if (dueDate == null) {
            throw new IllegalArgumentException("Due date cannot be null.");
        }
        this.dueDate = dueDate;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        if (priority == null) {
            throw new IllegalArgumentException("Priority cannot be null.");
        }
        this.priority = priority;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null.");
        }
        this.status = status;
    }

    @Override
    public String toString() {
        return "Task{id=" + getId() + ", userId=" + getUserId() +
               ", title='" + title + "', dueDate=" + dueDate +
               ", priority=" + priority + ", status=" + status + "}";
    }
}

