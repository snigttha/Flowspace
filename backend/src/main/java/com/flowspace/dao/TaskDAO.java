package com.flowspace.dao;

import com.flowspace.db.DBConnection;
import com.flowspace.model.Task;
import com.flowspace.model.Task.Priority;
import com.flowspace.model.Task.Status;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Data Access Object for the {@code tasks} table.
 *
 * <p>Implements {@link Dao}{@code <Task>} with queries strictly scoped to {@code user_id}.
 * Uses {@code idx_tasks_user_status} on {@code (user_id, status)}.
 *
 * @author Member 6 (Workspace & Integration Lead)
 */
public class TaskDAO implements Dao<Task> {

    private static final String SQL_INSERT =
        "INSERT INTO tasks (user_id, title, due_date, priority, status) VALUES (?, ?, ?, ?, ?)";

    private static final String SQL_FIND_BY_ID =
        "SELECT * FROM tasks WHERE task_id = ? AND user_id = ?";

    private static final String SQL_FIND_ALL =
        "SELECT * FROM tasks WHERE user_id = ? ORDER BY due_date ASC, created_at DESC";

    private static final String SQL_FIND_BY_STATUS =
        "SELECT * FROM tasks WHERE user_id = ? AND status = ? ORDER BY due_date ASC";

    private static final String SQL_UPDATE =
        "UPDATE tasks SET title = ?, due_date = ?, priority = ?, status = ? WHERE task_id = ? AND user_id = ?";

    private static final String SQL_UPDATE_STATUS =
        "UPDATE tasks SET status = ? WHERE task_id = ? AND user_id = ?";

    private static final String SQL_DELETE =
        "DELETE FROM tasks WHERE task_id = ? AND user_id = ?";

    private static final String SQL_COUNT_BY_STATUS =
        "SELECT status, COUNT(*) AS cnt FROM tasks WHERE user_id = ? GROUP BY status";

    protected Connection openConnection() throws SQLException {
        return DBConnection.get();
    }

    @Override
    public Task create(Task task) throws SQLException {
        task.validate();
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt   (1, task.getUserId());
            ps.setString(2, task.getTitle());
            ps.setString(3, task.getDueDate().toString());
            ps.setString(4, task.getPriority().name());
            ps.setString(5, task.getStatus().name());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int generated = keys.getInt(1);
                    return findById(generated, task.getUserId())
                        .orElseThrow(() -> new SQLException("Insert succeeded but task row not found"));
                }
            }
        }
        throw new SQLException("Insert did not return a generated key");
    }

    @Override
    public Optional<Task> findById(int id, int userId) throws SQLException {
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(SQL_FIND_BY_ID)) {
            ps.setInt(1, id);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    @Override
    public List<Task> findAll(int userId) throws SQLException {
        List<Task> list = new ArrayList<>();
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(SQL_FIND_ALL)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    public List<Task> findByStatus(int userId, Status status) throws SQLException {
        List<Task> list = new ArrayList<>();
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(SQL_FIND_BY_STATUS)) {
            ps.setInt(1, userId);
            ps.setString(2, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    @Override
    public Optional<Task> update(Task task) throws SQLException {
        task.validate();
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(SQL_UPDATE)) {
            ps.setString(1, task.getTitle());
            ps.setString(2, task.getDueDate().toString());
            ps.setString(3, task.getPriority().name());
            ps.setString(4, task.getStatus().name());
            ps.setInt   (5, task.getId());
            ps.setInt   (6, task.getUserId());
            int affected = ps.executeUpdate();
            if (affected == 0) return Optional.empty();
        }
        return findById(task.getId(), task.getUserId());
    }

    public boolean updateStatus(int taskId, int userId, Status status) throws SQLException {
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(SQL_UPDATE_STATUS)) {
            ps.setString(1, status.name());
            ps.setInt(2, taskId);
            ps.setInt(3, userId);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean delete(int id, int userId) throws SQLException {
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(SQL_DELETE)) {
            ps.setInt(1, id);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public Map<Status, Integer> countByStatus(int userId) throws SQLException {
        Map<Status, Integer> counts = new EnumMap<>(Status.class);
        for (Status s : Status.values()) {
            counts.put(s, 0);
        }
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(SQL_COUNT_BY_STATUS)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        Status s = Status.fromString(rs.getString("status"));
                        counts.put(s, rs.getInt("cnt"));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }
        return counts;
    }

    private Task map(ResultSet rs) throws SQLException {
        Task t = new Task();
        t.setId(rs.getInt("task_id"));
        t.setUserId(rs.getInt("user_id"));
        t.setTitle(rs.getString("title"));
        String due = rs.getString("due_date");
        if (due != null) {
            t.setDueDate(LocalDate.parse(due.substring(0, 10)));
        }
        t.setPriority(Priority.fromString(rs.getString("priority")));
        t.setStatus(Status.fromString(rs.getString("status")));

        String created = rs.getString("created_at");
        if (created != null) {
            t.setCreatedAt(LocalDateTime.parse(created.replace(" ", "T").substring(0, 19)));
        }
        return t;
    }
}

