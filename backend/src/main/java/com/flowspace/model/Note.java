package com.flowspace.model;

import com.flowspace.util.ValidationException;
import java.time.LocalDateTime;

/**
 * Concrete entity representing a Note in FlowSpace.
 * Inherits from WorkspaceItem (OOP Inheritance).
 */
public class Note extends WorkspaceItem {
    private String title;
    private String content;
    private String category;
    private LocalDateTime updatedAt;

    public Note() {
        super();
        this.category = "General";
        this.updatedAt = LocalDateTime.now();
    }

    public Note(int id, int userId, String title, String content, String category, 
                LocalDateTime createdAt, LocalDateTime updatedAt) {
        super(id, userId, createdAt);
        setTitle(title);
        setContent(content);
        setCategory(category);
        this.updatedAt = updatedAt != null ? updatedAt : LocalDateTime.now();
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new ValidationException("title", "Note title cannot be empty.");
        }
        if (title.length() > 100) {
            throw new ValidationException("title", "Note title cannot exceed 100 characters.");
        }
        this.title = title.trim();
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new ValidationException("content", "Note content cannot be empty.");
        }
        if (content.length() > 2000) {
            throw new ValidationException("content", "Note content cannot exceed 2000 characters.");
        }
        this.content = content.trim();
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = (category != null && !category.trim().isEmpty()) ? category.trim() : "General";
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public void validate() throws ValidationException {
        if (title == null || title.trim().isEmpty()) {
            throw new ValidationException("title", "Note title cannot be empty.");
        }
        if (title.length() > 100) {
            throw new ValidationException("title", "Note title cannot exceed 100 characters.");
        }
        if (content == null || content.trim().isEmpty()) {
            throw new ValidationException("content", "Note content cannot be empty.");
        }
        if (content.length() > 2000) {
            throw new ValidationException("content", "Note content cannot exceed 2000 characters.");
        }
    }

    @Override
    public String summary() {
        String snippet = (content != null && content.length() > 60) ? content.substring(0, 57) + "..." : content;
        return "Note [" + category + "]: " + title + " - " + snippet;
    }

    @Override
    public String getItemType() {
        return "NOTE";
    }
}
