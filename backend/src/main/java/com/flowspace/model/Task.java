package com.flowspace.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * Domain model for a user task.
 *
 * <p>Extends {@link WorkspaceItem} (inheritance) and provides:
 * <ul>
 *   <li>Private fields with validated setters (encapsulation)</li>
 *   <li>Server-side validation: title 3–100 chars, valid due date</li>
 *   <li>{@link #validate()} required by the abstract parent</li>
 *   <li>{@link #summary()} for polymorphic global search (Member 5)</li>
 *   <li>{@link #markComplete()}, {@link #reopen()}, {@link #edit} helpers</li>
 * </ul>
 *
 * <p>Field names match the SQL column names (PLAN.md API contract):
 * {@code task_id, user_id, title, due_date, priority, status, created_at}
 *
 * @author Member 3 – Tasks module
 */
public class Task extends WorkspaceItem {

    public static final int MIN_TITLE_LENGTH = 3;
    public static final int MAX_TITLE_LENGTH = 100;

    private String   title;
    private String   dueDate; // Stored as ISO date string: YYYY-MM-DD or YYYY-MM-DDTHH:mm
    private Priority priority = Priority.MEDIUM;
    private Status   status   = Status.TODO;

    // ── Constructors ─────────────────────────────────────────────────────

    /** Default constructor for DAO hydration. */
    public Task() {
        super();
        setCreatedAt(LocalDateTime.now());
    }

    /**
     * Full constructor for creating a new task.
     *
     * @param userId   owner's user id
     * @param title    task title (3 – 100 chars)
     * @param dueDate  due date string (YYYY-MM-DD)
     * @param priority task priority
     * @param status   initial status
     */
    public Task(int userId, String title, String dueDate, Priority priority, Status status) {
        setUserId(userId);
        setTitle(title);
        setDueDate(dueDate);
        this.priority = (priority != null) ? priority : Priority.MEDIUM;
        this.status   = (status != null)   ? status   : Status.TODO;
        setCreatedAt(LocalDateTime.now());
    }

    public Task(int userId, String title, String dueDate, Priority priority) {
        this(userId, title, dueDate, priority, Status.TODO);
    }

    // ── WorkspaceItem contract ───────────────────────────────────────────

    @Override
    public void validate() {
        if (title == null || title.trim().length() < MIN_TITLE_LENGTH || title.trim().length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException(
                "Task title must be between " + MIN_TITLE_LENGTH + " and " + MAX_TITLE_LENGTH + " characters");
        }
        if (dueDate == null || dueDate.trim().isEmpty()) {
            throw new IllegalArgumentException("Task due date is required");
        }
        // Validate date format
        parseDueDateAsLocalDate();

        if (getUserId() <= 0) {
            throw new IllegalArgumentException("Task must be associated with a valid user_id (> 0)");
        }
    }

    @Override
    public String summary() {
        return String.format("[Task] %s (Priority: %s, Due: %s, Status: %s)",
            title, priority, dueDate, status);
    }

    // ── Business logic & State Machine ───────────────────────────────────

    /**
     * Transitions status according to the state machine rules:
     * DONE tasks can only transition to TODO (reopen).
     */
    public void transitionStatus(Status nextStatus) {
        if (nextStatus == null) {
            throw new IllegalArgumentException("Target status cannot be null");
        }
        if (!this.status.canTransitionTo(nextStatus)) {
            throw new IllegalStateException(
                "Invalid status transition: Cannot move from " + this.status + " to " + nextStatus +
                ". Completed tasks must be reopened to To Do first.");
        }
        this.status = nextStatus;
    }

    public void markComplete() {
        transitionStatus(Status.DONE);
    }

    public void reopen() {
        transitionStatus(Status.TODO);
    }

    public void edit(String newTitle, String newDueDate, Priority newPriority) {
        setTitle(newTitle);
        setDueDate(newDueDate);
        if (newPriority != null) this.priority = newPriority;
        validate();
    }

    /**
     * Determines whether the task is overdue.
     * Rule: Completed tasks are never overdue.
     */
    public boolean isOverdue() {
        if (status == Status.DONE) {
            return false;
        }
        LocalDate due = parseDueDateAsLocalDate();
        return due != null && LocalDate.now().isAfter(due);
    }

    private LocalDate parseDueDateAsLocalDate() {
        if (dueDate == null || dueDate.isBlank()) return null;
        try {
            String clean = dueDate.trim();
            if (clean.contains("T")) {
                clean = clean.split("T")[0];
            } else if (clean.contains(" ")) {
                clean = clean.split(" ")[0];
            }
            return LocalDate.parse(clean);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid due date format: " + dueDate + ". Expected YYYY-MM-DD");
        }
    }

    // ── Getters / Setters ────────────────────────────────────────────────

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = (title != null) ? title.trim() : null;
    }

    public String getDueDate() {
        return dueDate;
    }

    public void setDueDate(String dueDate) {
        this.dueDate = (dueDate != null) ? dueDate.trim() : null;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        if (priority != null) this.priority = priority;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        transitionStatus(status);
    }
}
