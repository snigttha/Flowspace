package com.flowspace.service;

import com.flowspace.dao.PeriodLogDAO;
import com.flowspace.model.PeriodLog;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

public class PeriodTracker {

    private final PeriodLogDAO dao;

    public PeriodTracker() {
        this(new PeriodLogDAO());
    }

    PeriodTracker(PeriodLogDAO dao) {
        this.dao = dao;
    }

    public PeriodLog logCycle(int userId, LocalDate startDate, int cycleLength) throws SQLException {
        return dao.save(new PeriodLog(userId, startDate, cycleLength));
    }

    public List<PeriodLog> getCycles(int userId) throws SQLException {
        return dao.findAll(userId);
    }

    public Prediction predictNextPeriod(List<PeriodLog> cycles) {
        if (cycles == null || cycles.isEmpty()) return null;
        List<PeriodLog> sorted = cycles.stream()
            .sorted(Comparator.comparing(PeriodLog::getCycleStartDate))
            .toList();
        PeriodLog latest = sorted.get(sorted.size() - 1);
        long averageGap = latest.getCycleLength();
        if (sorted.size() > 1) {
            long totalGap = 0;
            for (int i = 1; i < sorted.size(); i++) {
                totalGap += ChronoUnit.DAYS.between(
                    sorted.get(i - 1).getCycleStartDate(), sorted.get(i).getCycleStartDate());
            }
            averageGap = Math.round((double) totalGap / (sorted.size() - 1));
        }
        int boundedGap = (int) Math.max(PeriodLog.MIN_CYCLE_LENGTH,
            Math.min(PeriodLog.MAX_CYCLE_LENGTH, averageGap));
        return new Prediction(sorted.size(), boundedGap, latest.getCycleStartDate(),
            latest.getCycleStartDate().plusDays(boundedGap));
    }

    public record Prediction(int loggedCycles, int averageCycleDays,
                             LocalDate latestStartDate, LocalDate predictedNextDate) {}
}