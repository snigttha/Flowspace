package com.flowspace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.flowspace.model.PeriodLog;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PeriodTrackerTest {

    private final PeriodTracker tracker = new PeriodTracker(null);

    @Test
    void predictsNoDateWithZeroLogs() {
        assertNull(tracker.predictNextPeriod(List.of()));
    }

    @Test
    void usesEnteredLengthForOneLog() {
        var prediction = tracker.predictNextPeriod(List.of(log("2026-09-01", 31)));
        assertEquals(31, prediction.averageCycleDays());
        assertEquals(LocalDate.parse("2026-10-02"), prediction.predictedNextDate());
    }

    @Test
    void usesGapForTwoLogs() {
        var prediction = tracker.predictNextPeriod(List.of(log("2026-08-01", 28), log("2026-08-25", 28)));
        assertEquals(24, prediction.averageCycleDays());
        assertEquals(LocalDate.parse("2026-09-18"), prediction.predictedNextDate());
    }

    @Test
    void averagesFiveLogsAndRoundsOddGapAverage() {
        var prediction = tracker.predictNextPeriod(List.of(
            log("2026-04-14", 28), log("2026-01-01", 28), log("2026-03-19", 28),
            log("2026-02-21", 28), log("2026-01-25", 28)));
        assertEquals(26, prediction.averageCycleDays());
        assertEquals(LocalDate.parse("2026-05-10"), prediction.predictedNextDate());
    }

    @Test
    void limitsAverageGapToFifteenThroughSixtyDays() {
        assertEquals(15, tracker.predictNextPeriod(
            List.of(log("2026-08-01", 28), log("2026-08-13", 28))).averageCycleDays());
        assertEquals(60, tracker.predictNextPeriod(
            List.of(log("2026-06-01", 28), log("2026-09-01", 28))).averageCycleDays());
    }

    private PeriodLog log(String date, int length) {
        return new PeriodLog(1, LocalDate.parse(date), length);
    }
}