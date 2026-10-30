package com.flowspace.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class PeriodLog extends WorkspaceItem {

    public static final int MIN_CYCLE_LENGTH = 15;
    public static final int MAX_CYCLE_LENGTH = 60;

    private LocalDate cycleStartDate;
    private int cycleLength;

    public PeriodLog() {
        setCreatedAt(LocalDateTime.now());
    }

    public PeriodLog(int userId, LocalDate cycleStartDate, int cycleLength) {
        setUserId(userId);
        setCycleStartDate(cycleStartDate);
        setCycleLength(cycleLength);
        setCreatedAt(LocalDateTime.now());
        validate();
    }

    @Override
    public void validate() {
        if (getUserId() <= 0) throw new IllegalArgumentException("A valid user is required.");
        if (cycleStartDate == null) throw new IllegalArgumentException("Cycle start date is required.");
        if (cycleStartDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Cycle start date cannot be in the future.");
        }
        if (cycleLength < MIN_CYCLE_LENGTH || cycleLength > MAX_CYCLE_LENGTH) {
            throw new IllegalArgumentException("Cycle length must be between 15 and 60 days.");
        }
    }

    @Override
    public String summary() {
        return "[Period] Start date " + cycleStartDate + " (" + cycleLength + " day cycle)";
    }

    public LocalDate getCycleStartDate() { return cycleStartDate; }

    public void setCycleStartDate(LocalDate cycleStartDate) { this.cycleStartDate = cycleStartDate; }

    public int getCycleLength() { return cycleLength; }

    public void setCycleLength(int cycleLength) { this.cycleLength = cycleLength; }
}