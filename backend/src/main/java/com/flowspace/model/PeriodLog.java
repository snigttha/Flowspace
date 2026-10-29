package com.flowspace.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Domain model for a user's cycle / period log.
 *
 * <p>Extends {@link WorkspaceItem} (inheritance) and provides:
 * <ul>
 *   <li>Private fields with validated setters (encapsulation)</li>
 *   <li>Validation: cycleStartDate not in future, cycleLength between 15 and 60 days</li>
 *   <li>{@link #validate()} and {@link #summary()} implementations</li>
 * </ul>
 *
 * <p>Field names match the SQL columns in {@code period_logs}:
 * {@code log_id, user_id, cycle_start_date, cycle_length, predicted_next_date}
 *
 * @author Member 6 (Workspace & Integration Lead)
 */
public class PeriodLog extends WorkspaceItem {

    public static final int MIN_CYCLE_LENGTH = 15;
    public static final int MAX_CYCLE_LENGTH = 60;
    public static final int DEFAULT_CYCLE_LENGTH = 28;

    private LocalDate cycleStartDate;
    private int       cycleLength = DEFAULT_CYCLE_LENGTH;
    private LocalDate predictedNextDate;

    /** Default constructor for DAO hydration. */
    public PeriodLog() {}

    public PeriodLog(int userId, LocalDate cycleStartDate, int cycleLength) {
        setUserId(userId);
        setCycleStartDate(cycleStartDate);
        setCycleLength(cycleLength);
        setCreatedAt(LocalDateTime.now());
        if (cycleStartDate != null) {
            this.predictedNextDate = cycleStartDate.plusDays(this.cycleLength);
        }
    }

    public PeriodLog(int userId, LocalDate cycleStartDate, int cycleLength, LocalDate predictedNextDate) {
        this(userId, cycleStartDate, cycleLength);
        this.predictedNextDate = predictedNextDate;
    }

    // ── WorkspaceItem contract ───────────────────────────────────────────

    @Override
    public void validate() {
        if (cycleStartDate == null) {
            throw new IllegalArgumentException("Cycle start date is required.");
        }
        if (cycleStartDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Cycle start date cannot be in the future.");
        }
        if (cycleLength < MIN_CYCLE_LENGTH || cycleLength > MAX_CYCLE_LENGTH) {
            throw new IllegalArgumentException(
                "Cycle length must be between " + MIN_CYCLE_LENGTH + " and " + MAX_CYCLE_LENGTH + " days.");
        }
    }

    @Override
    public String summary() {
        return "[Cycle] Start: " + cycleStartDate + " (Length: " + cycleLength + " days" +
               (predictedNextDate != null ? ", Next: " + predictedNextDate : "") + ")";
    }

    // ── Getters / validated setters ──────────────────────────────────────

    public LocalDate getCycleStartDate() {
        return cycleStartDate;
    }

    public void setCycleStartDate(LocalDate cycleStartDate) {
        if (cycleStartDate == null) {
            throw new IllegalArgumentException("Cycle start date cannot be null.");
        }
        this.cycleStartDate = cycleStartDate;
    }

    public int getCycleLength() {
        return cycleLength;
    }

    public void setCycleLength(int cycleLength) {
        if (cycleLength < MIN_CYCLE_LENGTH || cycleLength > MAX_CYCLE_LENGTH) {
            throw new IllegalArgumentException(
                "Cycle length must be between " + MIN_CYCLE_LENGTH + " and " + MAX_CYCLE_LENGTH + " days.");
        }
        this.cycleLength = cycleLength;
    }

    public LocalDate getPredictedNextDate() {
        return predictedNextDate;
    }

    public void setPredictedNextDate(LocalDate predictedNextDate) {
        this.predictedNextDate = predictedNextDate;
    }

    @Override
    public String toString() {
        return "PeriodLog{id=" + getId() + ", userId=" + getUserId() +
               ", cycleStartDate=" + cycleStartDate + ", cycleLength=" + cycleLength +
               ", predictedNextDate=" + predictedNextDate + "}";
    }
}

