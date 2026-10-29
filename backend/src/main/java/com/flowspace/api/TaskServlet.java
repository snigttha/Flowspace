package com.flowspace.api;

import com.flowspace.dao.TaskDAO;
import com.flowspace.model.Task;
import com.flowspace.model.Task.Priority;
import com.flowspace.model.Task.Status;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API servlet for the Tasks module.
 *
 * <p>Routes:
 * <pre>
 *   GET    /api/tasks           – list (with optional ?status=)
 *   POST   /api/tasks           – create
 *   PUT    /api/tasks/{id}      – update
 *   DELETE /api/tasks/{id}      – delete
 * </pre>
 *
 * @author Member 6 (Workspace & Integration Lead)
 */
@WebServlet(urlPatterns = {"/api/tasks", "/api/tasks/*"})
public class TaskServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final TaskDAO dao = new TaskDAO();
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        int userId = requireSession(req, resp);
        if (userId < 0) return;

        String statusParam = req.getParameter("status");
        try {
            List<Task> tasks;
            if (statusParam != null && !statusParam.isBlank()) {
                tasks = dao.findByStatus(userId, Status.fromString(statusParam));
            } else {
                tasks = dao.findAll(userId);
            }
            sendJson(resp, HttpServletResponse.SC_OK, tasksToJson(tasks));
        } catch (SQLException e) {
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        int userId = requireSession(req, resp);
        if (userId < 0) return;

        JsonObject body = parseBody(req, resp);
        if (body == null) return;

        String title = jsonString(body, "title");
        String due = jsonString(body, "due_date") != null ? jsonString(body, "due_date") : jsonString(body, "due");
        String priorityStr = jsonString(body, "priority");

        try {
            if (title == null || title.isBlank()) {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Title is required.");
                return;
            }
            if (due == null || due.isBlank()) {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Due date is required.");
                return;
            }
            LocalDate dueDate = LocalDate.parse(due.trim());
            Priority priority = Priority.fromString(priorityStr);

            Task task = new Task(userId, title, dueDate, priority);
            Task created = dao.create(task);
            sendJson(resp, HttpServletResponse.SC_CREATED, taskToJson(created));
        } catch (IllegalArgumentException e) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (SQLException e) {
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        int userId = requireSession(req, resp);
        if (userId < 0) return;

        int taskId = extractId(req, resp);
        if (taskId < 0) return;

        JsonObject body = parseBody(req, resp);
        if (body == null) return;

        try {
            var existingOpt = dao.findById(taskId, userId);
            if (existingOpt.isEmpty()) {
                sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Task not found.");
                return;
            }
            Task existing = existingOpt.get();

            if (body.has("title")) existing.setTitle(jsonString(body, "title"));
            String due = jsonString(body, "due_date") != null ? jsonString(body, "due_date") : jsonString(body, "due");
            if (due != null) existing.setDueDate(LocalDate.parse(due));
            if (body.has("priority")) existing.setPriority(Priority.fromString(jsonString(body, "priority")));
            if (body.has("status")) existing.setStatus(Status.fromString(jsonString(body, "status")));

            var updated = dao.update(existing);
            sendJson(resp, HttpServletResponse.SC_OK, taskToJson(updated.get()));
        } catch (IllegalArgumentException e) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (SQLException e) {
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        int userId = requireSession(req, resp);
        if (userId < 0) return;

        int taskId = extractId(req, resp);
        if (taskId < 0) return;

        try {
            boolean deleted = dao.delete(taskId, userId);
            if (deleted) {
                sendJson(resp, HttpServletResponse.SC_OK, Map.of("message", "Task deleted."));
            } else {
                sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Task not found.");
            }
        } catch (SQLException e) {
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        }
    }

    private int requireSession(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Please log in.");
            return -1;
        }
        return (int) session.getAttribute("userId");
    }

    private int extractId(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String info = req.getPathInfo();
        if (info == null || info.equals("/")) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Task id is required.");
            return -1;
        }
        try {
            String[] parts = info.split("/");
            return Integer.parseInt(parts[1]);
        } catch (Exception e) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid task id.");
            return -1;
        }
    }

    private JsonObject parseBody(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            return JsonParser.parseReader(req.getReader()).getAsJsonObject();
        } catch (Exception e) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid JSON body: " + e.getMessage());
            return null;
        }
    }

    private String jsonString(JsonObject obj, String key) {
        return (obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsString() : null;
    }

    private void sendJson(HttpServletResponse resp, int status, Object data) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        try (PrintWriter w = resp.getWriter()) {
            w.print(gson.toJson(data));
        }
    }

    private void sendError(HttpServletResponse resp, int status, String message) throws IOException {
        sendJson(resp, status, Map.of("error", message, "message", message));
    }

    private Map<String, Object> taskToJson(Task t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("task_id",    t.getId());
        m.put("user_id",    t.getUserId());
        m.put("title",      t.getTitle());
        m.put("due_date",   t.getDueDate() != null ? t.getDueDate().toString() : "");
        m.put("priority",   t.getPriority().name());
        m.put("status",     t.getStatus().name());
        m.put("created_at", t.getCreatedAt() != null ? t.getCreatedAt().toString() : "");
        return m;
    }

    private Object tasksToJson(List<Task> tasks) {
        return tasks.stream().map(this::taskToJson).toList();
    }
}

