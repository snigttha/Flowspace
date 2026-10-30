package com.flowspace.api;

import com.flowspace.dao.PeriodLogDAO;
import com.flowspace.model.AnalyticsData.CycleStats;
import com.flowspace.model.PeriodLog;
import com.flowspace.service.AnalyticsService;
import com.flowspace.model.PeriodLog;
import com.flowspace.service.PeriodTracker;
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
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API servlet for Menstrual Cycle logging and prediction.
 *
 * <p>Endpoints:
 * <pre>
 *   GET  /api/periods             – list all cycles
 *   POST /api/periods             – log or update a cycle
 *   GET  /api/periods/prediction  – cycle statistics and next predicted period
 * </pre>
 *
 * @author Member 6 (Workspace & Integration Lead)
 */
@WebServlet(urlPatterns = {"/api/periods", "/api/periods/*"})
public class PeriodServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final PeriodLogDAO dao = new PeriodLogDAO();
    private final AnalyticsService analyticsService = new AnalyticsService();
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        int userId = requireSession(req, resp);
        if (userId < 0) return;

        String path = req.getPathInfo();
        if ("/prediction".equals(path)) {
            handlePrediction(userId, resp);
            return;
        }

        try {
            List<PeriodLog> logs = dao.findAll(userId);
            sendJson(resp, HttpServletResponse.SC_OK, logsToJson(logs));
        } catch (SQLException e) {
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        }
    }

    private void handlePrediction(int userId, HttpServletResponse resp) throws IOException {
        try {
            List<PeriodLog> logs = dao.findAll(userId);
            CycleStats stats = analyticsService.computeCycleStats(logs);
            sendJson(resp, HttpServletResponse.SC_OK, stats);
        } catch (SQLException e) {
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
    private final PeriodTracker tracker = new PeriodTracker();
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int userId = requireSession(request, response);
        if (userId < 0) return;
        try {
            if ("/prediction".equals(request.getPathInfo())) {
                PeriodTracker.Prediction prediction = tracker.predictNextPeriod(tracker.getCycles(userId));
                if (prediction == null) {
                    sendJson(response, HttpServletResponse.SC_OK,
                        Map.of("logged_cycles", 0, "message", "Log your first cycle to see a prediction."));
                    return;
                }
                String message = "Next period expected around " + prediction.predictedNextDate()
                    + " (average cycle " + prediction.averageCycleDays() + " days, "
                    + prediction.loggedCycles() + " logged).";
                sendJson(response, HttpServletResponse.SC_OK, Map.of(
                    "logged_cycles", prediction.loggedCycles(),
                    "average_cycle_days", prediction.averageCycleDays(),
                    "latest_start_date", prediction.latestStartDate().toString(),
                    "predicted_next_date", prediction.predictedNextDate().toString(),
                    "message", message));
                return;
            }
            sendJson(response, HttpServletResponse.SC_OK,
                tracker.getCycles(userId).stream().map(this::toMap).toList());
        } catch (SQLException e) {
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Could not load cycle data.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        int userId = requireSession(req, resp);
        if (userId < 0) return;

        JsonObject body = parseBody(req, resp);
        if (body == null) return;

        String startStr = jsonString(body, "cycle_start_date") != null ?
                          jsonString(body, "cycle_start_date") : jsonString(body, "start");
        int length = 28;
        if (body.has("cycle_length")) length = body.get("cycle_length").getAsInt();
        else if (body.has("length")) length = body.get("length").getAsInt();

        try {
            if (startStr == null || startStr.isBlank()) {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Cycle start date is required.");
                return;
            }
            LocalDate startDate = LocalDate.parse(startStr.trim());
            PeriodLog log = new PeriodLog(userId, startDate, length);
            PeriodLog saved = dao.create(log);
            sendJson(resp, HttpServletResponse.SC_OK, periodToJson(saved));
        } catch (IllegalArgumentException e) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
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

    private Map<String, Object> periodToJson(PeriodLog p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("log_id",              p.getId());
        m.put("user_id",             p.getUserId());
        m.put("cycle_start_date",    p.getCycleStartDate() != null ? p.getCycleStartDate().toString() : "");
        m.put("cycle_length",        p.getCycleLength());
        m.put("predicted_next_date", p.getPredictedNextDate() != null ? p.getPredictedNextDate().toString() : "");
        return m;
    }

    private Object logsToJson(List<PeriodLog> logs) {
        return logs.stream().map(this::periodToJson).toList();
    }
}

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int userId = requireSession(request, response);
        if (userId < 0) return;
        JsonObject body;
        try {
            body = JsonParser.parseReader(request.getReader()).getAsJsonObject();
        } catch (RuntimeException e) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "A JSON object is required.");
            return;
        }

        try {
            String startText = body.has("cycle_start_date") ? body.get("cycle_start_date").getAsString() : null;
            if (startText == null || startText.isBlank() || !body.has("cycle_length")) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Cycle start date and cycle length are required.");
                return;
            }
            int length;
            try {
                length = new BigDecimal(body.get("cycle_length").getAsString()).intValueExact();
            } catch (NumberFormatException | ArithmeticException e) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Cycle length must be a whole number between 15 and 60 days.");
                return;
            }
            PeriodLog saved = tracker.logCycle(userId, LocalDate.parse(startText), length);
            sendJson(response, HttpServletResponse.SC_OK, toMap(saved));
        } catch (DateTimeParseException e) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Cycle start date must be a valid ISO date.");
        } catch (IllegalArgumentException e) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (SQLException e) {
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Could not save cycle data.");
        }
    }

    private int requireSession(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        Object value = session == null ? null : session.getAttribute("userId");
        if (value instanceof Number number && number.intValue() > 0) return number.intValue();
        sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Please log in.");
        return -1;
    }

    private Map<String, Object> toMap(PeriodLog log) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("log_id", log.getId());
        data.put("user_id", log.getUserId());
        data.put("cycle_start_date", log.getCycleStartDate().toString());
        data.put("cycle_length", log.getCycleLength());
        return data;
    }

    private void sendJson(HttpServletResponse response, int status, Object body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().print(gson.toJson(body));
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        sendJson(response, status, Map.of("error", message));
    }
}
