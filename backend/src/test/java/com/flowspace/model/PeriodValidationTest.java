package com.flowspace.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PeriodValidationTest {

    @Test
    void acceptsCycleLengthBounds() {
        assertDoesNotThrow(() -> new PeriodLog(1, LocalDate.now(), 15));
        assertDoesNotThrow(() -> new PeriodLog(1, LocalDate.now(), 60));
    }

    @Test
    void rejectsFutureStartDate() {
        assertThrows(IllegalArgumentException.class,
            () -> new PeriodLog(1, LocalDate.now().plusDays(1), 28));
    }

    @Test
    void rejectsLengthsOutsideBounds() {
        assertThrows(IllegalArgumentException.class, () -> new PeriodLog(1, LocalDate.now(), 14));
        assertThrows(IllegalArgumentException.class, () -> new PeriodLog(1, LocalDate.now(), 61));
    }
}