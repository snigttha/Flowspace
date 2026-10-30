package com.flowspace.backend;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring Boot REST Controller for Dashboard and Analytics metrics.
 * Serves GET /api/analytics and supplies real-time task counts, completion stats,
 * and recent activities from cloud MySQL.
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final DataSource dataSource;
    private final PeriodTracker periodTracker;

    public AnalyticsController(DataSource dataSource, PeriodTracker periodTracker) {
        this.dataSource = dataSource;
        this.periodTracker = periodTracker;
    }

    private int getUserId(HttpServletRequest req, HttpSession session) {
        if (session != null) {
            Object sUid = session.getAttribute("userId");
            if (sUid instanceof Number) return ((Number) sUid).intValue();
            if (sUid != null) {
                try { return Integer.parseInt(sUid.toString()); } catch (Exception ignored) {}
            }
        }
        String hUid = req.getHeader("X-User-Id");
        if (hUid != null && !hUid.isBlank()) {
            try { return Integer.parseInt(hUid.trim()); } catch (Exception ignored) {}
        }
        String param = req.getParameter("userId");
        if (param != null && !param.isBlank()) {
            try { return Integer.parseInt(param.trim()); } catch (Exception ignored) {}
        }
        return 1;
    }

    @GetMapping
    public ResponseEntity<?> getAnalytics(HttpServletRequest req, HttpSession session) {
        int userId = getUserId(req, session);

        int todo = 0;
        int inProgress = 0;
        int done = 0;
        List<Map<String, Object>> activities = new ArrayList<>();

        // 1. Query task metrics and recent activity from MySQL
        String countSql = "SELECT status, COUNT(*) AS cnt FROM tasks WHERE user_id = ? OR (user_id = 0 AND ? = 1) GROUP BY status";
        String recentSql = "SELECT title, status, created_at FROM tasks WHERE user_id = ? OR (user_id = 0 AND ? = 1) ORDER BY created_at DESC LIMIT 5";

        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(countSql)) {
                ps.setInt(1, userId);
                ps.setInt(2, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String st = rs.getString("status");
                        int c = rs.getInt("cnt");
                        if ("DONE".equalsIgnoreCase(st)) done += c;
                        else if ("IN_PROGRESS".equalsIgnoreCase(st)) inProgress += c;
                        else todo += c;
                    }
                }
            } catch (Exception ignored) {}

            try (PreparedStatement ps = conn.prepareStatement(recentSql)) {
                ps.setInt(1, userId);
                ps.setInt(2, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> act = new LinkedHashMap<>();
                        act.put("type", "TASK");
                        act.put("title", "Task: " + rs.getString("title"));
                        act.put("description", rs.getString("status"));
                        Timestamp ts = rs.getTimestamp("created_at");
                        String dt = ts != null ? ts.toLocalDateTime().toLocalDate().toString() : LocalDate.now().toString();
                        act.put("formatted_date", dt);
                        act.put("formattedDate", dt);
                        act.put("timestamp", ts != null ? ts.getTime() : System.currentTimeMillis());
                        activities.add(act);
                    }
                }
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}

        int total = todo + inProgress + done;
        int openTasks = todo + inProgress;
        int pct = total > 0 ? (int) Math.round(((double) done / total) * 100) : 0;

        // 2. Query period predictions if available
        boolean hasCycleData = false;
        String predictedNextDate = null;
        int loggedCycles = 0;
        if (periodTracker != null) {
            try {
                var logs = periodTracker.getCycles(userId);
                if (logs != null && !logs.isEmpty()) {
                    loggedCycles = logs.size();
                    var pred = periodTracker.predictNextPeriod(logs);
                    if (pred != null) {
                        hasCycleData = true;
                        predictedNextDate = pred.predictedNextDate() != null ? pred.predictedNextDate().toString() : null;
                    }
                }
            } catch (Exception ignored) {}
        }

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("notes", 0);
        counts.put("tasks", total);
        counts.put("open_tasks", openTasks);
        counts.put("openTasks", openTasks);
        counts.put("moods", 0);
        counts.put("cycles", loggedCycles);

        Map<String, Object> taskStats = new LinkedHashMap<>();
        taskStats.put("total", total);
        taskStats.put("completed", done);
        taskStats.put("pending", openTasks);
        taskStats.put("completion_percentage", pct);
        taskStats.put("completionPercentage", pct);

        Map<String, Object> cycleStats = new LinkedHashMap<>();
        cycleStats.put("has_data", hasCycleData);
        cycleStats.put("hasData", hasCycleData);
        cycleStats.put("predicted_next_date", predictedNextDate);
        cycleStats.put("predictedNextDate", predictedNextDate);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("counts", counts);
        response.put("task_stats", taskStats);
        response.put("taskStats", taskStats);
        response.put("cycle_stats", cycleStats);
        response.put("cycleStats", cycleStats);
        response.put("recent_activity", activities);
        response.put("recentActivity", activities);

        return ResponseEntity.ok(response);
    }
}
