package com.flowspace.web;

import com.flowspace.dao.MoodEntryDAO;
import com.flowspace.model.Mood;
import com.flowspace.model.MoodAverages;
import com.flowspace.model.MoodEntry;
import com.flowspace.util.ValidationException;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Endpoint for Mood Tracker: GET|POST|DELETE /api/moods
 * Owned by Member 5.
 */
@WebServlet(name = "MoodServlet", urlPatterns = {"/api/moods"})
public class MoodServlet extends HttpServlet {
    private final MoodEntryDAO moodDAO = new MoodEntryDAO();
    private final Gson gson = new Gson();

    private int getAuthenticatedUserId(HttpServletRequest req) {
        Object sessionUid = req.getSession().getAttribute("userId");
        if (sessionUid instanceof Integer) {
            return (Integer) sessionUid;
        }
        String headerUid = req.getHeader("X-User-Id");
        if (headerUid != null) {
            try { return Integer.parseInt(headerUid); } catch (NumberFormatException ignored) {}
        }
        // Default to demo user ID 1
        return 1;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        int userId = getAuthenticatedUserId(req);
        String averagesParam = req.getParameter("averages");
        String startParam = req.getParameter("start");
        String endParam = req.getParameter("end");
        String limitParam = req.getParameter("limit");

        try {
            if ("true".equalsIgnoreCase(averagesParam)) {
                MoodAverages avg = moodDAO.getAverages(userId);
                Map<String, Object> map = new HashMap<>();
                map.put("total_entries", avg.getTotalEntries());
                map.put("avg_sleep", avg.getAvgSleep());
                map.put("avg_water", avg.getAvgWater());
                map.put("avg_pain", avg.getAvgPain());
                resp.getWriter().write(gson.toJson(map));
                return;
            }

            if (startParam != null && endParam != null) {
                LocalDate start = LocalDate.parse(startParam);
                LocalDate end = LocalDate.parse(endParam);
                List<MoodEntry> list = moodDAO.findBetweenDates(userId, start, end);
                resp.getWriter().write(toJsonArray(list));
                return;
            }

            int limit = 15;
            if (limitParam != null) {
                try { limit = Integer.parseInt(limitParam); } catch (NumberFormatException ignored) {}
            }
            List<MoodEntry> list = moodDAO.findRecent(userId, limit);
            resp.getWriter().write(toJsonArray(list));

        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        int userId = getAuthenticatedUserId(req);

        try {
            // Read request body (JSON or Form)
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = req.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            MoodEntry entry = new MoodEntry();
            entry.setUserId(userId);

            String body = sb.toString().trim();
            if (body.startsWith("{")) {
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                String dateStr = json.has("date") ? json.get("date").getAsString() : json.get("entry_date").getAsString();
                entry.setEntryDate(LocalDate.parse(dateStr));
                entry.setMood(Mood.fromString(json.get("mood").getAsString()));
                entry.setSymptoms(json.has("symptoms") ? json.get("symptoms").getAsString() : null);
                entry.setPainLevel(json.has("pain") ? json.get("pain").getAsInt() : json.get("pain_level").getAsInt());
                entry.setSleepHours(json.has("sleep") ? json.get("sleep").getAsDouble() : json.get("sleep_hours").getAsDouble());
                entry.setWaterIntake(json.has("water") ? json.get("water").getAsInt() : json.get("water_intake").getAsInt());
            } else {
                // Form encoded
                entry.setEntryDate(LocalDate.parse(req.getParameter("date")));
                entry.setMood(Mood.fromString(req.getParameter("mood")));
                entry.setSymptoms(req.getParameter("symptoms"));
                entry.setPainLevel(Integer.parseInt(req.getParameter("pain")));
                entry.setSleepHours(Double.parseDouble(req.getParameter("sleep")));
                entry.setWaterIntake(Integer.parseInt(req.getParameter("water")));
            }

            // Server-side validation
            entry.validate();

            // Upsert: One entry per date, a second one updates it
            MoodEntry saved = moodDAO.upsert(entry);

            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(toJsonObject(saved));

        } catch (ValidationException ve) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\": \"" + ve.getMessage() + "\", \"field\": \"" + ve.getFieldName() + "\"}");
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\": \"Invalid input: " + e.getMessage() + "\"}");
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        int userId = getAuthenticatedUserId(req);

        String idParam = req.getParameter("id");
        String dateParam = req.getParameter("date");

        boolean deleted = false;
        if (idParam != null && !idParam.isEmpty()) {
            deleted = moodDAO.delete(Integer.parseInt(idParam), userId);
        } else if (dateParam != null && !dateParam.isEmpty()) {
            deleted = moodDAO.deleteByDate(LocalDate.parse(dateParam), userId);
        }

        if (deleted) {
            resp.getWriter().write("{\"success\": true}");
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"error\": \"Entry not found or unauthorized\"}");
        }
    }

    private String toJsonObject(MoodEntry m) {
        Map<String, Object> map = new HashMap<>();
        map.put("entry_id", m.getId());
        map.put("id", m.getId());
        map.put("user_id", m.getUserId());
        map.put("date", m.getEntryDate().toString());
        map.put("entry_date", m.getEntryDate().toString());
        map.put("mood", m.getMood() != null ? m.getMood().name() : null);
        map.put("symptoms", m.getSymptoms());
        map.put("pain", m.getPainLevel());
        map.put("pain_level", m.getPainLevel());
        map.put("sleep", m.getSleepHours());
        map.put("sleep_hours", m.getSleepHours());
        map.put("water", m.getWaterIntake());
        map.put("water_intake", m.getWaterIntake());
        return gson.toJson(map);
    }

    private String toJsonArray(List<MoodEntry> list) {
        List<Map<String, Object>> maps = list.stream().map(m -> {
            Map<String, Object> map = new HashMap<>();
            map.put("entry_id", m.getId());
            map.put("id", m.getId());
            map.put("user_id", m.getUserId());
            map.put("date", m.getEntryDate().toString());
            map.put("entry_date", m.getEntryDate().toString());
            map.put("mood", m.getMood() != null ? m.getMood().name() : null);
            map.put("symptoms", m.getSymptoms());
            map.put("pain", m.getPainLevel());
            map.put("pain_level", m.getPainLevel());
            map.put("sleep", m.getSleepHours());
            map.put("sleep_hours", m.getSleepHours());
            map.put("water", m.getWaterIntake());
            map.put("water_intake", m.getWaterIntake());
            return map;
        }).toList();
        return gson.toJson(maps);
    }
}
