package com.flowspace.api;

import com.flowspace.model.Priority;
import com.flowspace.model.Status;
import com.flowspace.model.Task;
import com.flowspace.service.TaskManager;
import com.google.gson.*;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST API servlet for the Tasks module.
 *
 * <p>Routes (PLAN.md API contract):
 * <pre>
 *   GET    /api/tasks           – list (with optional ?status=&sort= params)
 *   GET    /api/tasks/counts    – status counts for Member 6 analytics
 *   GET    /api/tasks/{id}      – get single task
 *   POST   /api/tasks           – create task
 *   PUT    /api/tasks/{id}      – update task
 *   PATCH  /api/tasks/{id}/status – update status
 *   DELETE /api/tasks/{id}      – delete task
 * </pre>
 *
 * @author Member 3 – Tasks module
 */
@WebServlet(urlPatterns = {"/api/tasks", "/api/tasks/*"})
public class TaskServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final TaskManager manager = new TaskManager();
    private final Gson gson = new GsonBuilder()
        .registerTypeAdapter(LocalDateTime.class,
            (JsonSerializer<LocalDateTime>) (src, t, ctx) ->
                ctx.serialize(src != null ? src.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null))
        .create();

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if ("PATCH".equalsIgnoreCase(req.getMethod())) {
            doPatch(req, resp);
        } else {
            super.service(req, resp);
        }
    }

    // ── GET /api/tasks, /api/tasks/{id}, /api/tasks/counts ──────────────

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int userId = requireSession(req, resp);
        if (userId < 0) return;

        String path = req.getPathInfo();

        try {
            if (path != null && path.equals("/counts")) {
                Map<Status, Integer> counts = manager.getStatusCounts(userId);
                sendJson(resp, HttpServletResponse.SC_OK, gson.toJson(counts));
                return;
            }

            Integer taskId = parseIdFromPath(path);
            if (taskId != null) {
                Optional<Task> opt = manager.getTask(taskId, userId);
                if (opt.isPresent()) {
                    sendJson(resp, HttpServletResponse.SC_OK, taskToJson(opt.get()));
                } else {
                    sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Task not found");
                }
            } else {
                String statusParam = req.getParameter("status");
                String sortParam = req.getParameter("sort");
                List<Task> tasks = manager.getTasks(userId, statusParam, sortParam);
                sendJson(resp, HttpServletResponse.SC_OK, tasksToJson(tasks));
            }
        } catch (SQLException e) {
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        }
    }

    // ── POST /api/tasks ──────────────────────────────────────────────────

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int userId = requireSession(req, resp);
        if (userId < 0) return;

        JsonObject body = parseJsonBody(req, resp);
        if (body == null) return;

        String title = getJsonString(body, "title");
        String dueDate = getJsonString(body, "due_date");
        if (dueDate == null) {
            dueDate = getJsonString(body, "dueDate");
        }
        String priority = getJsonString(body, "priority");

        try {
            Task created = manager.createTask(userId, title, dueDate, priority);
            sendJson(resp, HttpServletResponse.SC_CREATED, taskToJson(created));
        } catch (IllegalArgumentException e) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (SQLException e) {
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        }
    }

    // ── PUT /api/tasks/{id} ──────────────────────────────────────────────

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int userId = requireSession(req, resp);
        if (userId < 0) return;

        Integer taskId = parseIdFromPath(req.getPathInfo());
        if (taskId == null) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing task id in path");
            return;
        }

        JsonObject body = parseJsonBody(req, resp);
        if (body == null) return;

        String title = getJsonString(body, "title");
        String dueDate = getJsonString(body, "due_date");
        if (dueDate == null) {
            dueDate = getJsonString(body, "dueDate");
        }
        String priority = getJsonString(body, "priority");

        try {
            Optional<Task> updated = manager.updateTask(taskId, userId, title, dueDate, priority);
            if (updated.isPresent()) {
                sendJson(resp, HttpServletResponse.SC_OK, taskToJson(updated.get()));
            } else {
                sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Task not found");
            }
        } catch (IllegalArgumentException e) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (SQLException e) {
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        }
    }

    // ── PATCH /api/tasks/{id}/status ─────────────────────────────────────

    protected void doPatch(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int userId = requireSession(req, resp);
        if (userId < 0) return;

        String path = req.getPathInfo();
        Integer taskId = parseIdFromPath(path);
        if (taskId == null) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing task id in path");
            return;
        }

        JsonObject body = parseJsonBody(req, resp);
        if (body == null) return;

        String status = getJsonString(body, "status");
        if (status == null || status.isBlank()) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "'status' is required");
            return;
        }

        try {
            Optional<Task> updated = manager.updateStatus(taskId, userId, status);
            if (updated.isPresent()) {
                sendJson(resp, HttpServletResponse.SC_OK, taskToJson(updated.get()));
            } else {
                sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Task not found");
            }
        } catch (IllegalStateException | IllegalArgumentException e) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (SQLException e) {
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        }
    }

    // ── DELETE /api/tasks/{id} ───────────────────────────────────────────

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int userId = requireSession(req, resp);
        if (userId < 0) return;

        Integer taskId = parseIdFromPath(req.getPathInfo());
        if (taskId == null) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing task id in path");
            return;
        }

        try {
            boolean deleted = manager.deleteTask(taskId, userId);
            if (deleted) {
                sendJson(resp, HttpServletResponse.SC_OK, "{\"message\":\"Task deleted\"}");
            } else {
                sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Task not found");
            }
        } catch (SQLException e) {
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private int requireSession(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session != null && session.getAttribute("userId") != null) {
            return (Integer) session.getAttribute("userId");
        }
        // Dev fallback for quick local testing without login
        String devHeader = req.getHeader("X-User-Id");
        if (devHeader != null) {
            try { return Integer.parseInt(devHeader); } catch (NumberFormatException ignored) {}
        }
        String paramId = req.getParameter("userId");
        if (paramId != null) {
            try { return Integer.parseInt(paramId); } catch (NumberFormatException ignored) {}
        }
        jakarta.servlet.http.Cookie[] cookies = req.getCookies();
        if (cookies != null) {
            for (jakarta.servlet.http.Cookie c : cookies) {
                if ("userId".equals(c.getName()) || "fs_session".equals(c.getName())) {
                    try { return Integer.parseInt(c.getValue()); } catch (NumberFormatException ignored) {}
                }
            }
        }
        sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Sign-in required");
        return -1;
    }

    private Integer parseIdFromPath(String pathInfo) {
        if (pathInfo == null || pathInfo.isEmpty()) return null;
        String[] parts = pathInfo.split("/");
        if (parts.length >= 2) {
            try {
                return Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private JsonObject parseJsonBody(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        try {
            JsonElement el = JsonParser.parseString(sb.toString());
            if (el.isJsonObject()) return el.getAsJsonObject();
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Expected JSON object");
            return null;
        } catch (JsonSyntaxException e) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid JSON: " + e.getMessage());
            return null;
        }
    }

    private String getJsonString(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return null;
    }

    private String taskToJson(Task task) {
        JsonObject obj = new JsonObject();
        obj.addProperty("task_id", task.getId());
        obj.addProperty("id", task.getId());
        obj.addProperty("user_id", task.getUserId());
        obj.addProperty("title", task.getTitle());
        obj.addProperty("due_date", task.getDueDate());
        obj.addProperty("dueDate", task.getDueDate());
        obj.addProperty("priority", task.getPriority().name());
        obj.addProperty("status", task.getStatus().name());
        obj.addProperty("overdue", task.isOverdue());
        if (task.getCreatedAt() != null) {
            obj.addProperty("created_at", task.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        return gson.toJson(obj);
    }

    private String tasksToJson(List<Task> tasks) {
        JsonArray arr = new JsonArray();
        for (Task t : tasks) {
            arr.add(JsonParser.parseString(taskToJson(t)));
        }
        return gson.toJson(arr);
    }

    private void sendJson(HttpServletResponse resp, int statusCode, String json) throws IOException {
        resp.setStatus(statusCode);
        resp.setContentType("application/json;charset=UTF-8");
        try (PrintWriter w = resp.getWriter()) {
            w.write(json);
        }
    }

    private void sendError(HttpServletResponse resp, int statusCode, String message) throws IOException {
        resp.setStatus(statusCode);
        resp.setContentType("application/json;charset=UTF-8");
        JsonObject err = new JsonObject();
        err.addProperty("error", message);
        try (PrintWriter w = resp.getWriter()) {
            w.write(gson.toJson(err));
        }
    }
}
