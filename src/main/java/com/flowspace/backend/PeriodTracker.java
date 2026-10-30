package com.flowspace.backend;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PeriodTracker {

    private final PeriodLogDAO dao;

    public PeriodTracker(PeriodLogDAO dao) {
        this.dao = dao;
    }

    public PeriodLog logCycle(int userId, LocalDate startDate, int cycleLength) throws SQLException {
        return dao.save(new PeriodLog(userId, startDate, cycleLength));
    }

    public List<PeriodLog> getCycles(int userId) throws SQLException {
        return dao.findAll(userId);
    }

    public CyclePrediction predictNextPeriod(List<PeriodLog> logs) {
        if (logs == null || logs.isEmpty()) return null;

        List<PeriodLog> sorted = logs.stream()
            .sorted(Comparator.comparing(PeriodLog::getCycleStartDate))
            .toList();
        PeriodLog latest = sorted.get(sorted.size() - 1);
        long averageGap = latest.getCycleLength();
        if (sorted.size() > 1) {
            long totalGap = 0;
            for (int i = 1; i < sorted.size(); i++) {
                totalGap += java.time.temporal.ChronoUnit.DAYS.between(
                    sorted.get(i - 1).getCycleStartDate(), sorted.get(i).getCycleStartDate());
            }
            averageGap = Math.round((double) totalGap / (sorted.size() - 1));
        }

        int boundedGap = (int) Math.max(PeriodLog.MIN_CYCLE_LENGTH,
            Math.min(PeriodLog.MAX_CYCLE_LENGTH, averageGap));
        return new CyclePrediction(sorted.size(), boundedGap, latest.getCycleStartDate(),
            latest.getCycleStartDate().plusDays(boundedGap));
    }

    public record CyclePrediction(int loggedCycles, int averageCycleDays,
                                  LocalDate latestStartDate, LocalDate predictedNextDate) {}
}