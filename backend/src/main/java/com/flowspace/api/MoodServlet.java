package com.flowspace.api;

import com.flowspace.dao.MoodEntryDAO;
import com.flowspace.model.MoodEntry;
import com.flowspace.model.MoodEntry.Mood;
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
 * REST API servlet for the Mood & Wellness tracking module.
 *
 * <p>Endpoints:
 * <pre>
 *   GET  /api/moods – list mood entries
 *   POST /api/moods – log or update a mood entry for a date
 * </pre>
 *
 * @author Member 6 (Workspace & Integration Lead)
 */
@WebServlet(urlPatterns = {"/api/moods", "/api/moods/*"})
public class MoodServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final MoodEntryDAO dao = new MoodEntryDAO();
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        int userId = requireSession(req, resp);
        if (userId < 0) return;

        try {
            List<MoodEntry> entries = dao.findAll(userId);
            sendJson(resp, HttpServletResponse.SC_OK, moodsToJson(entries));
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

        String dateStr = jsonString(body, "entry_date") != null ?
                         jsonString(body, "entry_date") : jsonString(body, "date");
        String moodStr = jsonString(body, "mood");
        String symptoms = jsonString(body, "symptoms");

        int pain = 0;
        if (body.has("pain_level")) pain = body.get("pain_level").getAsInt();
        else if (body.has("pain")) pain = body.get("pain").getAsInt();

        double sleep = 7.0;
        if (body.has("sleep_hours")) sleep = body.get("sleep_hours").getAsDouble();
        else if (body.has("sleep")) sleep = body.get("sleep").getAsDouble();

        int water = 6;
        if (body.has("water_intake")) water = body.get("water_intake").getAsInt();
        else if (body.has("water")) water = body.get("water").getAsInt();

        try {
            if (dateStr == null || dateStr.isBlank()) {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Date is required.");
                return;
            }
            if (moodStr == null || moodStr.isBlank()) {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Mood is required.");
                return;
            }

            LocalDate date = LocalDate.parse(dateStr.trim());
            Mood mood = Mood.fromString(moodStr);

            MoodEntry entry = new MoodEntry(userId, date, mood, symptoms, pain, sleep, water);
            MoodEntry saved = dao.create(entry);
            sendJson(resp, HttpServletResponse.SC_OK, moodToJson(saved));
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
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid JSON format in request body.");
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
        String clientMsg = message;
        if (status >= 500) {
            System.err.println("MoodServlet Error [" + status + "]: " + message);
            clientMsg = "An internal server error occurred. Please try again later.";
        }
        sendJson(resp, status, Map.of("error", clientMsg, "message", clientMsg));
    }

    private Map<String, Object> moodToJson(MoodEntry m) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("entry_id",     m.getId());
        map.put("user_id",      m.getUserId());
        map.put("entry_date",   m.getEntryDate() != null ? m.getEntryDate().toString() : "");
        map.put("mood",         m.getMood().name());
        map.put("symptoms",     m.getSymptoms() != null ? m.getSymptoms() : "");
        map.put("pain_level",   m.getPainLevel());
        map.put("sleep_hours",  m.getSleepHours());
        map.put("water_intake", m.getWaterIntake());
        return map;
    }

    private Object moodsToJson(List<MoodEntry> moods) {
        return moods.stream().map(this::moodToJson).toList();
    }
}

