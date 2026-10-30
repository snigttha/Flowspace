package com.flowspace.api;

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
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@WebServlet(urlPatterns = {"/api/periods", "/api/periods/*"})
public class PeriodServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
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