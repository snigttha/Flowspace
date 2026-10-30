package com.flowspace.backend;

import java.time.LocalDateTime;

public abstract class WorkspaceItem {

    private int id;
    private int userId;
    private LocalDateTime createdAt;

    protected WorkspaceItem() {}

    protected WorkspaceItem(int userId) {
        setUserId(userId);
    }

    public abstract void validate();

    public abstract String summary();

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
}