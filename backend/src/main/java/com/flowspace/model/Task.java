package com.flowspace.model;

import com.flowspace.util.ValidationException;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Concrete entity representing a Task in FlowSpace.
 * Inherits from WorkspaceItem (OOP Inheritance).
 */
public class Task extends WorkspaceItem {
    private String title;
    private LocalDate dueDate;
    private String priority; // LOW, MEDIUM, HIGH
    private String status;   // TODO, IN_PROGRESS, DONE

    public Task() {
        super();
        this.priority = "MEDIUM";
        this.status = "TODO";
    }

    public Task(int id, int userId, String title, LocalDate dueDate, 
                String priority, String status, LocalDateTime createdAt) {
        super(id, userId, createdAt);
        setTitle(title);
        setDueDate(dueDate);
        setPriority(priority);
        setStatus(status);
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new ValidationException("title", "Task title cannot be empty.");
        }
        if (title.length() < 3 || title.length() > 100) {
            throw new ValidationException("title", "Task title must be between 3 and 100 characters.");
        }
        this.title = title.trim();
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        if (dueDate == null) {
            throw new ValidationException("dueDate", "Due date is required.");
        }
        this.dueDate = dueDate;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        if (priority == null || (!priority.equals("LOW") && !priority.equals("MEDIUM") && !priority.equals("HIGH"))) {
            throw new ValidationException("priority", "Priority must be LOW, MEDIUM, or HIGH.");
        }
        this.priority = priority;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        if (status == null || (!status.equals("TODO") && !status.equals("IN_PROGRESS") && !status.equals("DONE"))) {
            throw new ValidationException("status", "Status must be TODO, IN_PROGRESS, or DONE.");
        }
        this.status = status;
    }

    @Override
    public void validate() throws ValidationException {
        if (title == null || title.trim().length() < 3 || title.trim().length() > 100) {
            throw new ValidationException("title", "Task title must be between 3 and 100 characters.");
        }
        if (dueDate == null) {
            throw new ValidationException("dueDate", "Due date is required.");
        }
        if (priority == null || (!priority.equals("LOW") && !priority.equals("MEDIUM") && !priority.equals("HIGH"))) {
            throw new ValidationException("priority", "Priority must be LOW, MEDIUM, or HIGH.");
        }
        if (status == null || (!status.equals("TODO") && !status.equals("IN_PROGRESS") && !status.equals("DONE"))) {
            throw new ValidationException("status", "Status must be TODO, IN_PROGRESS, or DONE.");
        }
    }

    @Override
    public String summary() {
        return "Task (" + priority + "): " + title + " - Due " + dueDate + " [" + status + "]";
    }

    @Override
    public String getItemType() {
        return "TASK";
    }
}
