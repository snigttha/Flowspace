package com.flowspace.model;

import java.time.LocalDateTime;

/**
 * Abstract base for every user-owned workspace item.
 *
 * <p>Implements the shared class design described in PLAN.md:
 * <ul>
 *   <li>Private fields with validated setters (encapsulation)</li>
 *   <li>Abstract {@link #validate()} and {@link #summary()} (abstraction)</li>
 *   <li>Extended by Note, Task, MoodEntry, PeriodLog (inheritance)</li>
 *   <li>Global search calls {@code summary()} polymorphically</li>
 * </ul>
 *
 * @author Member 2 – Notes module (base class shared with all members)
 */
public abstract class WorkspaceItem {

    private int           id;
    private int           userId;
    private LocalDateTime createdAt;

    // ── constructors ────────────────────────────────────────────────────

    protected WorkspaceItem() {}

    protected WorkspaceItem(int id, int userId, LocalDateTime createdAt) {
        this.id        = id;
        this.userId    = userId;
        this.createdAt = createdAt;
    }

    // ── abstract contract ────────────────────────────────────────────────

    /**
     * Validates the item's own fields.
     * Implementations throw {@link IllegalArgumentException} when a rule is broken.
     */
    public abstract void validate();

    /**
     * Returns a short, human-readable one-line summary used by the global
     * search endpoint to present items of different types uniformly
     * (polymorphism demonstration).
     */
    public abstract String summary();

    // ── getters / validated setters ─────────────────────────────────────

    public int getId() { return id; }

    public void setId(int id) {
        if (id < 0) throw new IllegalArgumentException("id must be non-negative");
        this.id = id;
    }

    public int getUserId() { return userId; }

    public void setUserId(int userId) {
        if (userId <= 0) throw new IllegalArgumentException("userId must be positive");
        this.userId = userId;
    }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{id=" + id + ", userId=" + userId + "}";
    }
}
