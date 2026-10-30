package com.flowspace.api;

import com.flowspace.model.Note;
import com.flowspace.service.NoteManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * REST API servlet for the Notes module.
 *
 * <p>Routes (PLAN.md API contract):
 * <pre>
 *   GET    /api/notes           – list (with optional ?q=&category= params)
 *   POST   /api/notes           – create
 *   PUT    /api/notes/{id}      – update
 *   DELETE /api/notes/{id}      – delete
 * </pre>
 *
 * <p>All responses are JSON. The session must contain a {@code userId}
 * attribute (set by Member 1's AuthService / login endpoint); otherwise
 * every request receives a 401 Unauthorized response.
 *
 * @author Member 2 – Notes module
 */
@WebServlet(urlPatterns = {"/api/notes", "/api/notes/*"})
public class NoteServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final NoteManager manager = new NoteManager();
    private final Gson gson = new GsonBuilder()
        .registerTypeAdapter(LocalDateTime.class,
            (com.google.gson.JsonSerializer<LocalDateTime>) (src, t, ctx) ->
                ctx.serialize(src.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)))
        .create();

    // ── GET /api/notes?q=&category= ─────────────────────────────────────

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        int userId = requireSession(req, resp);
        if (userId < 0) return;

        String q        = req.getParameter("q");
        String category = req.getParameter("category");

        try {
            List<Note> notes = manager.getNotes(userId, q, category);
            sendJson(resp, HttpServletResponse.SC_OK, notesToJson(notes));
        } catch (SQLException e) {
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                      "Database error: " + e.getMessage());
        }
    }

    // ── POST /api/notes ─────────────────────────────────────────────────

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        int userId = requireSession(req, resp);
        if (userId < 0) return;

        JsonObject body = parseBody(req, resp);
        if (body == null) return;

        String title    = jsonString(body, "title");
        String content  = jsonString(body, "content");
        String category = jsonString(body, "category");

        try {
            Note created = manager.createNote(userId, title, content, category);
            sendJson(resp, HttpServletResponse.SC_CREATED, noteToJson(created));
        } catch (IllegalArgumentException e) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (SQLException e) {
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                      "Database error: " + e.getMessage());
        }
    }

    // ── PUT /api/notes/{id} ──────────────────────────────────────────────

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        int userId = requireSession(req, resp);
        if (userId < 0) return;

        int noteId = extractId(req, resp);
        if (noteId < 0) return;

        JsonObject body = parseBody(req, resp);
        if (body == null) return;

        String title    = jsonString(body, "title");
        String content  = jsonString(body, "content");
        String category = jsonString(body, "category");

        try {
            Note updated = manager.updateNote(noteId, userId, title, content, category);
            sendJson(resp, HttpServletResponse.SC_OK, noteToJson(updated));
        } catch (IllegalArgumentException e) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (SecurityException e) {
            sendError(resp, HttpServletResponse.SC_FORBIDDEN, e.getMessage());
        } catch (SQLException e) {
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                      "Database error: " + e.getMessage());
        }
    }

    // ── DELETE /api/notes/{id} ───────────────────────────────────────────

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        int userId = requireSession(req, resp);
        if (userId < 0) return;

        int noteId = extractId(req, resp);
        if (noteId < 0) return;

        try {
            boolean deleted = manager.deleteNote(noteId, userId);
            if (deleted) {
                sendJson(resp, HttpServletResponse.SC_OK,
                         Map.of("message", "Note deleted."));
            } else {
                sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Note not found.");
            }
        } catch (IllegalArgumentException e) {
            sendError(resp, HttpServletResponse.SC_NOT_FOUND, e.getMessage());
        } catch (SecurityException e) {
            sendError(resp, HttpServletResponse.SC_FORBIDDEN, e.getMessage());
        } catch (SQLException e) {
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                      "Database error: " + e.getMessage());
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /** Returns the userId from the session, or writes 401 and returns -1. */
    private int requireSession(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Please log in.");
            return -1;
        }
        return (int) session.getAttribute("userId");
    }

    /** Extracts the trailing /{id} path segment, or writes 400 and returns -1. */
    private int extractId(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        String info = req.getPathInfo(); // e.g. "/42"
        if (info == null || info.equals("/")) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Note id is required.");
            return -1;
        }
        try {
            return Integer.parseInt(info.substring(1));
        } catch (NumberFormatException e) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid note id.");
            return -1;
        }
    }

    /** Reads the request body as a JSON object, or writes 400 and returns null. */
    private JsonObject parseBody(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        try {
            return JsonParser.parseReader(req.getReader()).getAsJsonObject();
        } catch (Exception e) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST,
                      "Invalid JSON format in request body.");
            return null;
        }
    }

    private String jsonString(JsonObject obj, String key) {
        return (obj.has(key) && !obj.get(key).isJsonNull())
            ? obj.get(key).getAsString() : null;
    }

    private void sendJson(HttpServletResponse resp, int status, Object data)
            throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        try (PrintWriter w = resp.getWriter()) {
            w.print(gson.toJson(data));
        }
    }

    private void sendError(HttpServletResponse resp, int status, String message)
            throws IOException {
        String clientMsg = message;
        if (status >= 500) {
            System.err.println("NoteServlet Error [" + status + "]: " + message);
            clientMsg = "An internal server error occurred. Please try again later.";
        }
        sendJson(resp, status, Map.of("error", clientMsg, "message", clientMsg));
    }

    // ── Note → JSON serialisation (field names match SQL columns) ────────

    private Map<String, Object> noteToJson(Note n) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("note_id",    n.getId());
        m.put("user_id",    n.getUserId());
        m.put("title",      n.getTitle());
        m.put("content",    n.getContent());
        m.put("category",   n.getCategory().name());
        m.put("created_at", n.getCreatedAt() != null ? n.getCreatedAt().toString() : "");
        m.put("updated_at", n.getUpdatedAt() != null ? n.getUpdatedAt().toString() : "");
        return m;
    }

    private Object notesToJson(List<Note> notes) {
        return notes.stream().map(this::noteToJson).toList();
    }
}
