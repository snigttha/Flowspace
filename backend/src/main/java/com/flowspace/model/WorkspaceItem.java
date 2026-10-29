package com.flowspace.model;

import com.flowspace.util.ValidationException;
import java.time.LocalDateTime;

/**
 * Abstract base class demonstrating OOP Abstraction and Inheritance.
 * Extended by Note, Task, MoodEntry, and PeriodLog.
 * 
 * Enforces encapsulation via private fields and requires child classes to implement
 * validation rules and polymorphic summary generation.
 */
public abstract class WorkspaceItem {
    private int id;
    private int userId;
    private LocalDateTime createdAt;

    public WorkspaceItem() {
        this.createdAt = LocalDateTime.now();
    }

    public WorkspaceItem(int id, int userId, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    // Encapsulation: getters and setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        if (userId < 0) {
            throw new ValidationException("userId", "User ID cannot be negative.");
        }
        this.userId = userId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Abstract method enforcing server-side business rule validation.
     * Must be implemented by every concrete workspace item.
     * 
     * @throws ValidationException if validation constraints are violated.
     */
    public abstract void validate() throws ValidationException;

    /**
     * Abstract method demonstrating Polymorphism in FlowSpace.
     * Returns a human-readable summary of the item for global search & activity feeds.
     */
    public abstract String summary();

    /**
     * Returns the type identifier of this workspace item (e.g. "MOOD", "NOTE", "TASK").
     */
    public abstract String getItemType();
}
