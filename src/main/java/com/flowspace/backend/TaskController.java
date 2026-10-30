package com.flowspace.backend;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import javax.sql.DataSource;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final DataSource dataSource;

    public TaskController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void initTable() {
        // Auto-create tasks table if not existing in the connected MySQL/SQLite DB
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS tasks (
                    task_id INT AUTO_INCREMENT PRIMARY KEY,
                    user_id INT NOT NULL,
                    title VARCHAR(100) NOT NULL,
                    due_date VARCHAR(20) NOT NULL,
                    priority VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',
                    status VARCHAR(20) NOT NULL DEFAULT 'TODO',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_tasks_user_status (user_id, status)
                )
            """);
        } catch (Exception e) {
            // Some databases might use different DDL; ignore if table already exists
        }
    }

    private int getUserId(HttpServletRequest req, HttpSession session) {
        if (session != null && session.getAttribute("userId") instanceof Number num) {
            return num.intValue();
        }
        String header = req.getHeader("X-User-Id");
        if (header != null && !header.isBlank()) {
            try { return Integer.parseInt(header); } catch (NumberFormatException ignored) {}
        }
        String param = req.getParameter("userId");
        if (param != null && !param.isBlank()) {
            try { return Integer.parseInt(param); } catch (NumberFormatException ignored) {}
        }
        return 1; // Default to demo user 1 if running locally
    }

    @GetMapping
    public ResponseEntity<?> listTasks(
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "due_date") String sort,
            HttpServletRequest req,
            HttpSession session) {

        int userId = getUserId(req, session);
        String sql = "SELECT * FROM tasks WHERE user_id = ?";
        if (status != null && !status.isBlank()) {
            sql += " AND status = ?";
        }
        if ("priority".equalsIgnoreCase(sort)) {
            sql += " ORDER BY CASE priority WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 WHEN 'LOW' THEN 3 ELSE 4 END ASC";
        } else {
            sql += " ORDER BY due_date ASC";
        }

        List<Map<String, Object>> tasks = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            if (status != null && !status.isBlank()) {
                ps.setString(2, status.toUpperCase());
            }

            try (ResultSet rs = ps.executeQuery()) {
                String today = LocalDate.now().toString();
                while (rs.next()) {
                    Map<String, Object> task = new LinkedHashMap<>();
                    int id = rs.getInt("task_id");
                    String tStatus = rs.getString("status");
                    String dueDate = rs.getString("due_date");
                    boolean overdue = tStatus != null && !"DONE".equalsIgnoreCase(tStatus)
                                      && dueDate != null && dueDate.compareTo(today) < 0;

                    task.put("task_id", id);
                    task.put("id", id);
                    task.put("user_id", rs.getInt("user_id"));
                    task.put("title", rs.getString("title"));
                    task.put("due_date", dueDate);
                    task.put("dueDate", dueDate);
                    task.put("priority", rs.getString("priority"));
                    task.put("status", tStatus);
                    task.put("overdue", overdue);
                    Timestamp created = rs.getTimestamp("created_at");
                    task.put("created_at", created != null ? created.toString() : null);

                    tasks.add(task);
                }
            }
            return ResponseEntity.ok(tasks);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Database error: " + e.getMessage()));
        }
    }

    @GetMapping("/counts")
    public ResponseEntity<?> getCounts(HttpServletRequest req, HttpSession session) {
        int userId = getUserId(req, session);
        Map<String, Integer> counts = new HashMap<>();
        counts.put("TODO", 0);
        counts.put("IN_PROGRESS", 0);
        counts.put("DONE", 0);

        String sql = "SELECT status, COUNT(*) AS cnt FROM tasks WHERE user_id = ? GROUP BY status";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    counts.put(rs.getString("status"), rs.getInt("cnt"));
                }
            }
            return ResponseEntity.ok(counts);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> createTask(
            @RequestBody Map<String, String> body,
            HttpServletRequest req,
            HttpSession session) {

        int userId = getUserId(req, session);
        String title = body.get("title");
        String dueDate = body.get("dueDate");
        if (dueDate == null) dueDate = body.get("due_date");
        String priority = body.getOrDefault("priority", "MEDIUM").toUpperCase();

        if (title == null || title.trim().length() < 3 || title.trim().length() > 100) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Title must be between 3 and 100 characters"));
        }
        if (dueDate == null || dueDate.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Due date is required"));
        }

        String sql = "INSERT INTO tasks (user_id, title, due_date, priority, status) VALUES (?, ?, ?, ?, 'TODO')";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, userId);
            ps.setString(2, title.trim());
            ps.setString(3, dueDate.trim());
            ps.setString(4, priority);

            ps.executeUpdate();

            int id = 0;
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    id = rs.getInt(1);
                }
            }

            Map<String, Object> created = new LinkedHashMap<>();
            created.put("task_id", id);
            created.put("id", id);
            created.put("user_id", userId);
            created.put("title", title.trim());
            created.put("due_date", dueDate.trim());
            created.put("dueDate", dueDate.trim());
            created.put("priority", priority);
            created.put("status", "TODO");
            created.put("overdue", false);

            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Could not save task: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateTask(
            @PathVariable int id,
            @RequestBody Map<String, String> body,
            HttpServletRequest req,
            HttpSession session) {

        int userId = getUserId(req, session);
        String title = body.get("title");
        String dueDate = body.get("dueDate");
        if (dueDate == null) dueDate = body.get("due_date");
        String priority = body.getOrDefault("priority", "MEDIUM").toUpperCase();
        String status = body.get("status");

        if (title != null && (title.trim().length() < 3 || title.trim().length() > 100)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Title must be between 3 and 100 characters"));
        }

        String sql = "UPDATE tasks SET title = ?, due_date = ?, priority = ?" +
                     (status != null ? ", status = ?" : "") +
                     " WHERE task_id = ? AND user_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, title != null ? title.trim() : "");
            ps.setString(2, dueDate != null ? dueDate.trim() : "");
            ps.setString(3, priority);
            if (status != null) {
                ps.setString(4, status.toUpperCase());
                ps.setInt(5, id);
                ps.setInt(6, userId);
            } else {
                ps.setInt(4, id);
                ps.setInt(5, userId);
            }

            int affected = ps.executeUpdate();
            if (affected == 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Task not found"));
            }

            return ResponseEntity.ok(Map.of("message", "Task updated successfully", "id", id));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(
            @PathVariable int id,
            @RequestBody Map<String, String> body,
            HttpServletRequest req,
            HttpSession session) {

        int userId = getUserId(req, session);
        String newStatus = body.get("status");
        if (newStatus == null || newStatus.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Status is required"));
        }
        newStatus = newStatus.toUpperCase();

        // Check state machine rule: DONE -> only TODO
        String selectSql = "SELECT status FROM tasks WHERE task_id = ? AND user_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement checkPs = conn.prepareStatement(selectSql)) {

            checkPs.setInt(1, id);
            checkPs.setInt(2, userId);
            try (ResultSet rs = checkPs.executeQuery()) {
                if (!rs.next()) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(Map.of("error", "Task not found"));
                }
                String current = rs.getString("status");
                if ("DONE".equalsIgnoreCase(current) && "IN_PROGRESS".equalsIgnoreCase(newStatus)) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Completed tasks must be reopened to To Do first"));
                }
            }

            String updateSql = "UPDATE tasks SET status = ? WHERE task_id = ? AND user_id = ?";
            try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
                updatePs.setString(1, newStatus);
                updatePs.setInt(2, id);
                updatePs.setInt(3, userId);
                updatePs.executeUpdate();
            }

            return ResponseEntity.ok(Map.of("message", "Status updated", "status", newStatus));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTask(
            @PathVariable int id,
            HttpServletRequest req,
            HttpSession session) {

        int userId = getUserId(req, session);
        String sql = "DELETE FROM tasks WHERE task_id = ? AND user_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            ps.setInt(2, userId);
            int affected = ps.executeUpdate();
            if (affected == 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Task not found"));
            }
            return ResponseEntity.ok(Map.of("message", "Task deleted"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
