package com.flowspace.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PeriodTrackerTest {

    private final PeriodTracker tracker = new PeriodTracker(null);

    @Test
    void predictsNothingWithZeroLogs() {
        assertNull(tracker.predictNextPeriod(List.of()));
    }

    @Test
    void usesEnteredLengthForOneLog() {
        var prediction = tracker.predictNextPeriod(List.of(log("2026-09-01", 31)));
        assertEquals(31, prediction.averageCycleDays());
        assertEquals(LocalDate.parse("2026-10-02"), prediction.predictedNextDate());
    }

    @Test
    void predictsFromOneObservedGap() {
        var prediction = tracker.predictNextPeriod(List.of(
            log("2026-08-01", 28), log("2026-08-25", 28)));
        assertEquals(24, prediction.averageCycleDays());
        assertEquals(LocalDate.parse("2026-09-18"), prediction.predictedNextDate());
    }

    @Test
    void averagesFiveLogsAndRoundsHalfDaysUp() {
        var prediction = tracker.predictNextPeriod(List.of(
            log("2026-04-13", 28), log("2026-01-01", 28), log("2026-03-18", 28),
            log("2026-02-21", 28), log("2026-01-25", 28)));
        assertEquals(26, prediction.averageCycleDays());
        assertEquals(LocalDate.parse("2026-05-09"), prediction.predictedNextDate());
    }

    @Test
    void clampsObservedGapsToAllowedRange() {
        var shortGap = tracker.predictNextPeriod(List.of(log("2026-08-01", 28), log("2026-08-13", 28)));
        var longGap = tracker.predictNextPeriod(List.of(log("2026-06-01", 28), log("2026-09-01", 28)));
        assertEquals(15, shortGap.averageCycleDays());
        assertEquals(60, longGap.averageCycleDays());
    }

    @Test
    void rejectsFutureDatesAndInvalidLengths() {
        assertThrows(IllegalArgumentException.class,
            () -> new PeriodLog(1, LocalDate.now().plusDays(1), 28));
        assertThrows(IllegalArgumentException.class,
            () -> new PeriodLog(1, LocalDate.now(), 14));
        assertThrows(IllegalArgumentException.class,
            () -> new PeriodLog(1, LocalDate.now(), 61));
    }

    private PeriodLog log(String start, int length) {
        return new PeriodLog(1, LocalDate.parse(start), length);
    }
}