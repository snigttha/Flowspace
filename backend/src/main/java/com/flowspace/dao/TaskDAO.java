package com.flowspace.dao;

import com.flowspace.model.Task;
import com.flowspace.util.DBConnection;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Tasks.
 * Provides search capability for Member 5's Global Search service.
 */
public class TaskDAO implements Dao<Task> {

    @Override
    public Task create(Task task) {
        task.validate();
        String sql = "INSERT INTO tasks (user_id, title, due_date, priority, status) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, task.getUserId());
            ps.setString(2, task.getTitle());
            ps.setString(3, task.getDueDate().toString());
            ps.setString(4, task.getPriority());
            ps.setString(5, task.getStatus());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) task.setId(rs.getInt(1));
            }
            return task;
        } catch (SQLException e) {
            throw new RuntimeException("Error inserting task: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean update(Task task) {
        task.validate();
        String sql = "UPDATE tasks SET title = ?, due_date = ?, priority = ?, status = ? WHERE task_id = ? AND user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, task.getTitle());
            ps.setString(2, task.getDueDate().toString());
            ps.setString(3, task.getPriority());
            ps.setString(4, task.getStatus());
            ps.setInt(5, task.getId());
            ps.setInt(6, task.getUserId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Error updating task: " + e.getMessage(), e);
        }
    }

    @Override
    public Task findById(int id, int userId) {
        String sql = "SELECT task_id, user_id, title, due_date, priority, status, created_at FROM tasks WHERE task_id = ? AND user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Task(rs.getInt("task_id"), rs.getInt("user_id"),
                            rs.getString("title"), LocalDate.parse(rs.getString("due_date")),
                            rs.getString("priority"), rs.getString("status"), null);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding task: " + e.getMessage(), e);
        }
        return null;
    }

    @Override
    public List<Task> findAll(int userId) {
        return search(userId, "");
    }

    public List<Task> search(int userId, String keyword) {
        String sql = "SELECT task_id, user_id, title, due_date, priority, status FROM tasks " +
                     "WHERE user_id = ? AND (LOWER(title) LIKE ? OR LOWER(priority) LIKE ? OR LOWER(status) LIKE ?) " +
                     "ORDER BY due_date ASC LIMIT 20";
        List<Task> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String term = "%" + keyword.toLowerCase().trim() + "%";
            ps.setInt(1, userId);
            ps.setString(2, term);
            ps.setString(3, term);
            ps.setString(4, term);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Task(rs.getInt("task_id"), rs.getInt("user_id"),
                            rs.getString("title"), LocalDate.parse(rs.getString("due_date")),
                            rs.getString("priority"), rs.getString("status"), null));
                }
            }
        } catch (SQLException e) {
            return list;
        }
        return list;
    }

    @Override
    public boolean delete(int id, int userId) {
        String sql = "DELETE FROM tasks WHERE task_id = ? AND user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting task: " + e.getMessage(), e);
        }
    }
}
