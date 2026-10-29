package com.flowspace.service;

import com.flowspace.dao.TaskDAO;
import com.flowspace.model.Priority;
import com.flowspace.model.Status;
import com.flowspace.model.Task;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service layer for the Tasks module.
 *
 * <p>Coordinates between {@link com.flowspace.api.TaskServlet} and {@link TaskDAO}.
 * Responsible for:
 * <ul>
 *   <li>Server-side validation before write operations</li>
 *   <li>State machine transition verification</li>
 *   <li>Throwing {@link IllegalArgumentException} on validation failure</li>
 *   <li>Aggregating task counts for Member 6</li>
 * </ul>
 *
 * @author Member 3 – Tasks module
 */
public class TaskManager {

    private final TaskDAO dao;

    public TaskManager() {
        this.dao = new TaskDAO();
    }

    /** Package-private constructor for testing with mock or in-memory DAO. */
    TaskManager(TaskDAO dao) {
        this.dao = dao;
    }

    // ── CRUD Operations ──────────────────────────────────────────────────

    public Task createTask(int userId, String title, String dueDate, String priorityStr) throws SQLException {
        Priority priority = Priority.fromString(priorityStr);
        Task task = new Task(userId, title, dueDate, priority, Status.TODO);
        task.validate();
        return dao.create(task);
    }

    public Optional<Task> getTask(int taskId, int userId) throws SQLException {
        return dao.findById(taskId, userId);
    }

    public List<Task> getTasks(int userId, String statusStr, String sortBy) throws SQLException {
        Status status = (statusStr != null && !statusStr.isBlank()) ? Status.fromString(statusStr) : null;
        return dao.findByUserAndStatus(userId, status, sortBy);
    }

    public Optional<Task> updateTask(int taskId, int userId, String title, String dueDate, String priorityStr) throws SQLException {
        Optional<Task> opt = dao.findById(taskId, userId);
        if (opt.isEmpty()) {
            return Optional.empty();
        }

        Task task = opt.get();
        Priority priority = (priorityStr != null) ? Priority.fromString(priorityStr) : task.getPriority();
        task.edit(title, dueDate, priority);
        return dao.update(task);
    }

    public Optional<Task> updateStatus(int taskId, int userId, String statusStr) throws SQLException {
        Status newStatus = Status.fromString(statusStr);
        return dao.updateStatus(taskId, userId, newStatus);
    }

    public boolean deleteTask(int taskId, int userId) throws SQLException {
        return dao.delete(taskId, userId);
    }

    public Map<Status, Integer> getStatusCounts(int userId) throws SQLException {
        return dao.countByStatus(userId);
    }

    public List<Task> getOverdueTasks(int userId) throws SQLException {
        return dao.getOverdueTasks(userId);
    }
}
