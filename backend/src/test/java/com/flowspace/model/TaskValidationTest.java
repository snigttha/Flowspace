package com.flowspace.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Task} domain model.
 * Tests field constraints, state machine rules, overdue logic, and polymorphic summary.
 *
 * @author Member 3 – Tasks module
 */
@DisplayName("Task – model validation and state tests")
class TaskValidationTest {

    private static final int USER_ID = 42;

    @Test
    @DisplayName("Valid task passes validate()")
    void validTaskPasses() {
        Task t = new Task(USER_ID, "Build TaskDAO", "2026-11-15", Priority.HIGH);
        assertDoesNotThrow(t::validate);
        assertEquals("Build TaskDAO", t.getTitle());
        assertEquals(Priority.HIGH, t.getPriority());
        assertEquals(Status.TODO, t.getStatus());
        assertFalse(t.isOverdue());
    }

    @Test
    @DisplayName("Title shorter than 3 characters throws IllegalArgumentException")
    void titleTooShort() {
        Task t = new Task(USER_ID, "ab", "2026-11-15", Priority.MEDIUM);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, t::validate);
        assertTrue(ex.getMessage().contains("between 3 and 100 characters"));
    }

    @Test
    @DisplayName("Title longer than 100 characters throws IllegalArgumentException")
    void titleTooLong() {
        String longTitle = "A".repeat(101);
        Task t = new Task(USER_ID, longTitle, "2026-11-15", Priority.MEDIUM);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, t::validate);
        assertTrue(ex.getMessage().contains("between 3 and 100 characters"));
    }

    @Test
    @DisplayName("Null or blank title throws IllegalArgumentException")
    void titleNullOrBlank() {
        Task t1 = new Task(USER_ID, null, "2026-11-15", Priority.LOW);
        assertThrows(IllegalArgumentException.class, t1::validate);

        Task t2 = new Task(USER_ID, "   ", "2026-11-15", Priority.LOW);
        assertThrows(IllegalArgumentException.class, t2::validate);
    }

    @Test
    @DisplayName("Null or empty due date throws IllegalArgumentException")
    void dueDateRequired() {
        Task t = new Task(USER_ID, "Task title", null, Priority.LOW);
        assertThrows(IllegalArgumentException.class, t::validate);

        Task t2 = new Task(USER_ID, "Task title", "   ", Priority.LOW);
        assertThrows(IllegalArgumentException.class, t2::validate);
    }

    @Test
    @DisplayName("Invalid date format throws IllegalArgumentException")
    void invalidDateFormat() {
        Task t = new Task(USER_ID, "Task title", "15-11-2026", Priority.LOW);
        assertThrows(IllegalArgumentException.class, t::validate);
    }

    @Test
    @DisplayName("Invalid user_id (<= 0) throws IllegalArgumentException")
    void invalidUserId() {
        assertThrows(IllegalArgumentException.class, () ->
            new Task(0, "Valid title", "2026-11-15", Priority.LOW));
    }

    @Test
    @DisplayName("Status transition: TODO -> IN_PROGRESS -> DONE succeeds")
    void standardStatusTransitions() {
        Task t = new Task(USER_ID, "Implement APIs", "2026-11-20", Priority.HIGH);
        assertEquals(Status.TODO, t.getStatus());

        t.transitionStatus(Status.IN_PROGRESS);
        assertEquals(Status.IN_PROGRESS, t.getStatus());

        t.markComplete();
        assertEquals(Status.DONE, t.getStatus());
    }

    @Test
    @DisplayName("Status transition: DONE -> IN_PROGRESS is rejected by state machine")
    void doneCannotMoveDirectlyToInProgress() {
        Task t = new Task(USER_ID, "Implement APIs", "2026-11-20", Priority.HIGH);
        t.markComplete();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            t.transitionStatus(Status.IN_PROGRESS));
        assertTrue(ex.getMessage().contains("Completed tasks must be reopened to To Do first"));
    }

    @Test
    @DisplayName("Status transition: DONE can be reopened to TODO")
    void doneCanReopenToTodo() {
        Task t = new Task(USER_ID, "Implement APIs", "2026-11-20", Priority.HIGH);
        t.markComplete();
        assertEquals(Status.DONE, t.getStatus());

        t.reopen();
        assertEquals(Status.TODO, t.getStatus());
    }

    @Test
    @DisplayName("Overdue check: past date non-completed task is overdue")
    void pastDueDateIsOverdue() {
        String pastDate = LocalDate.now().minusDays(2).toString();
        Task t = new Task(USER_ID, "Submit project proposal", pastDate, Priority.HIGH);
        assertTrue(t.isOverdue());
    }

    @Test
    @DisplayName("Overdue check: completed task is NEVER overdue even if date passed")
    void completedTaskNeverOverdue() {
        String pastDate = LocalDate.now().minusDays(5).toString();
        Task t = new Task(USER_ID, "Submit sprint 1 retro", pastDate, Priority.MEDIUM);
        t.markComplete();
        assertFalse(t.isOverdue());
    }

    @Test
    @DisplayName("summary() returns polymorphic formatted one-liner")
    void polymorphicSummary() {
        Task t = new Task(USER_ID, "Refactor code", "2026-12-01", Priority.MEDIUM);
        String s = t.summary();
        assertTrue(s.startsWith("[Task] Refactor code"));
        assertTrue(s.contains("MEDIUM"));
        assertTrue(s.contains("TODO"));
    }
}
