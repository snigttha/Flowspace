package com.flowspace.backend;

import java.sql.SQLException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/periods")
public class PeriodsController {

    private final PeriodTracker tracker;

    public PeriodsController(PeriodTracker tracker) {
        this.tracker = tracker;
    }

    @GetMapping
    public ResponseEntity<?> getPeriods(HttpSession session) {
        Integer userId = sessionUserId(session);
        if (userId == null) return unauthorized();
        try {
            return ResponseEntity.ok(tracker.getCycles(userId).stream().map(this::toMap).toList());
        } catch (SQLException e) {
            return serverError();
        }
    }

    @GetMapping("/prediction")
    public ResponseEntity<?> getPrediction(HttpSession session) {
        Integer userId = sessionUserId(session);
        if (userId == null) return unauthorized();
        try {
            PeriodTracker.CyclePrediction prediction = tracker.predictNextPeriod(tracker.getCycles(userId));
            if (prediction == null) {
                return ResponseEntity.ok(Map.of(
                    "logged_cycles", 0,
                    "message", "Log your first cycle to see a prediction."
                ));
            }
            String message = "Next period expected around " + prediction.predictedNextDate()
                + " (average cycle " + prediction.averageCycleDays() + " days, "
                + prediction.loggedCycles() + " logged).";
            return ResponseEntity.ok(Map.of(
                "logged_cycles", prediction.loggedCycles(),
                "average_cycle_days", prediction.averageCycleDays(),
                "latest_start_date", prediction.latestStartDate().toString(),
                "predicted_next_date", prediction.predictedNextDate().toString(),
                "message", message
            ));
        } catch (SQLException e) {
            return serverError();
        }
    }

    @PostMapping
    public ResponseEntity<?> logPeriod(@RequestBody Map<String, Object> body, HttpSession session) {
        Integer userId = sessionUserId(session);
        if (userId == null) return unauthorized();

        try {
            Object rawDate = body.get("cycle_start_date");
            Object rawLength = body.get("cycle_length");
            if (!(rawDate instanceof String dateText) || !(rawLength instanceof Number length)) {
                return badRequest("Cycle start date and cycle length are required");
            }
            int cycleLength;
            try {
                cycleLength = new BigDecimal(length.toString()).intValueExact();
            } catch (NumberFormatException | ArithmeticException e) {
                return badRequest("Cycle length must be a whole number between 15 and 60 days");
            }
            PeriodLog saved = tracker.logCycle(userId, LocalDate.parse(dateText), cycleLength);
            return ResponseEntity.ok(toMap(saved));
        } catch (DateTimeParseException e) {
            return badRequest("Cycle start date must be a valid ISO date");
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        } catch (SQLException e) {
            return serverError();
        }
    }

    private Map<String, Object> toMap(PeriodLog log) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("log_id", log.getId());
        result.put("id", log.getId());
        result.put("user_id", log.getUserId());
        result.put("cycle_start_date", log.getCycleStartDate().toString());
        result.put("cycle_length", log.getCycleLength());
        return result;
    }

    private Integer sessionUserId(HttpSession session) {
        Object value = session.getAttribute("userId");
        return value instanceof Number number ? number.intValue() : null;
    }

    private ResponseEntity<Map<String, String>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please log in"));
    }

    private ResponseEntity<Map<String, String>> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    private ResponseEntity<Map<String, String>> serverError() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("error", "Could not access cycle data"));
    }
}