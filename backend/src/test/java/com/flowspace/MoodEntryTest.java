package com.flowspace;

import com.flowspace.model.Mood;
import com.flowspace.model.MoodEntry;
import com.flowspace.model.WorkspaceItem;
import com.flowspace.util.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MoodEntry model.
 * Verifies validation constraints, encapsulation, and polymorphic summary behavior.
 */
public class MoodEntryTest {

    @Test
    @DisplayName("Valid MoodEntry passes validation")
    void testValidMoodEntry() {
        MoodEntry entry = new MoodEntry(1, 1, LocalDate.now(), Mood.HAPPY, "Feeling great", 2, 8.0, 8, LocalDateTime.now());
        assertDoesNotThrow(entry::validate);
        assertEquals(Mood.HAPPY, entry.getMood());
        assertEquals(2, entry.getPainLevel());
        assertEquals(8.0, entry.getSleepHours());
        assertEquals(8, entry.getWaterIntake());
    }

    @Test
    @DisplayName("Future entry date is rejected")
    void testFutureDateRejected() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        MoodEntry entry = new MoodEntry();
        assertThrows(ValidationException.class, () -> entry.setEntryDate(tomorrow));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 11, 20})
    @DisplayName("Pain level out of bounds (0-10) is rejected")
    void testPainLevelBounds(int invalidPain) {
        MoodEntry entry = new MoodEntry();
        assertThrows(ValidationException.class, () -> entry.setPainLevel(invalidPain));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.5, 24.1, 30.0})
    @DisplayName("Sleep hours out of bounds (0-24) is rejected")
    void testSleepHoursBounds(double invalidSleep) {
        MoodEntry entry = new MoodEntry();
        assertThrows(ValidationException.class, () -> entry.setSleepHours(invalidSleep));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 31, 50})
    @DisplayName("Water intake out of bounds (0-30) is rejected")
    void testWaterIntakeBounds(int invalidWater) {
        MoodEntry entry = new MoodEntry();
        assertThrows(ValidationException.class, () -> entry.setWaterIntake(invalidWater));
    }

    @Test
    @DisplayName("Symptoms exceeding 200 characters are rejected")
    void testSymptomsLengthLimit() {
        MoodEntry entry = new MoodEntry();
        String longSymptoms = "A".repeat(201);
        assertThrows(ValidationException.class, () -> entry.setSymptoms(longSymptoms));
    }

    @Test
    @DisplayName("Null mood is rejected upon validation")
    void testNullMoodValidation() {
        MoodEntry entry = new MoodEntry();
        assertThrows(ValidationException.class, () -> entry.setMood(null));
    }

    @Test
    @DisplayName("Polymorphic summary() produces expected string")
    void testPolymorphicSummary() {
        LocalDate date = LocalDate.of(2026, 10, 20);
        WorkspaceItem item = new MoodEntry(5, 1, date, Mood.CALM, "Deep meditation", 1, 7.5, 9, LocalDateTime.now());

        // Call method polymorphically through WorkspaceItem interface
        String summary = item.summary();
        assertTrue(summary.contains("Calm"));
        assertTrue(summary.contains("7.5h"));
        assertTrue(summary.contains("9 cups"));
        assertTrue(summary.contains("1/10"));
        assertTrue(summary.contains("Deep meditation"));
        assertEquals("MOOD", item.getItemType());
    }

    @Test
    @DisplayName("Mood enum score and emoji mappings")
    void testMoodEnum() {
        assertEquals(5, Mood.HAPPY.getScore());
        assertEquals("😊", Mood.HAPPY.getEmoji());
        assertEquals(Mood.HAPPY, Mood.fromString("happy"));
        assertEquals(Mood.CALM, Mood.fromString("CALM"));
        assertThrows(IllegalArgumentException.class, () -> Mood.fromString("EXCITED"));
    }
}
