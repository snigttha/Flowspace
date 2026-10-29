package com.flowspace.api;

import com.flowspace.model.AnalyticsData;
import com.flowspace.model.Workspace;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * REST API servlet for FlowSpace Analytics and Dashboard metrics.
 *
 * <p>Route (PLAN.md API contract):
 * <pre>
 *   GET /api/analytics
 * </pre>
 *
 * <p>Returns dashboard counters, task statistics, cycle statistics,
 * mood trends, recent activity, and personalized written insights
 * scoped strictly to the authenticated user.
 *
 * <p>All responses are JSON. Requires a valid session cookie containing
 * {@code userId}; returns 401 Unauthorized when unauthenticated.
 *
 * @author Member 6 (Workspace & Integration Lead)
 */
@WebServlet(urlPatterns = {"/api/analytics"})
public class AnalyticsServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final Gson gson = new GsonBuilder()
        .registerTypeAdapter(LocalDate.class,
            (com.google.gson.JsonSerializer<LocalDate>) (src, t, ctx) ->
                ctx.serialize(src != null ? src.format(DateTimeFormatter.ISO_LOCAL_DATE) : null))
        .registerTypeAdapter(LocalDateTime.class,
            (com.google.gson.JsonSerializer<LocalDateTime>) (src, t, ctx) ->
                ctx.serialize(src != null ? src.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null))
        .serializeNulls()
        .create();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        int userId = requireSession(req, resp);
        if (userId < 0) return;

        try {
            Workspace workspace = createWorkspace(userId);
            AnalyticsData analytics = workspace.getAnalytics();
            sendJson(resp, HttpServletResponse.SC_OK, analytics);
        } catch (SQLException e) {
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                      "Database error while loading analytics: " + e.getMessage());
        } catch (Exception e) {
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                      "Unexpected error while generating analytics: " + e.getMessage());
        }
    }

    /**
     * Hook to create Workspace instance (overridable in tests).
     */
    protected Workspace createWorkspace(int userId) {
        return new Workspace(userId);
    }

    private int requireSession(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Please log in.");
            return -1;
        }
        Object uid = session.getAttribute("userId");
        if (uid instanceof Number) {
            return ((Number) uid).intValue();
        }
        sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Invalid session.");
        return -1;
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
        sendJson(resp, status, Map.of("error", message, "message", message));
    }
}

