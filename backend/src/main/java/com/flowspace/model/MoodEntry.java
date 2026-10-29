package com.flowspace.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Domain model for a user's daily mood and wellness log.
 *
 * <p>Extends {@link WorkspaceItem} (inheritance) and provides:
 * <ul>
 *   <li>Private fields with validated setters (encapsulation)</li>
 *   <li>Validation: entryDate not in future, pain 0..10, sleep 0..24, water 0..30</li>
 *   <li>Mood scale mapping (HAPPY: 5, CALM: 4, NEUTRAL: 3, TIRED: 2, ANXIOUS: 2, IRRITABLE: 2, SAD: 1)</li>
 *   <li>{@link #validate()} and {@link #summary()} implementations</li>
 * </ul>
 *
 * <p>Field names match the SQL columns in {@code mood_entries}:
 * {@code entry_id, user_id, entry_date, mood, symptoms, pain_level, sleep_hours, water_intake}
 *
 * @author Member 6 (Workspace & Integration Lead)
 */
public class MoodEntry extends WorkspaceItem {

    public enum Mood {
        HAPPY(5),
        CALM(4),
        NEUTRAL(3),
        TIRED(2),
        ANXIOUS(2),
        IRRITABLE(2),
        SAD(1);

        private final int score;

        Mood(int score) {
            this.score = score;
        }

        public int getScore() {
            return score;
        }

        public static Mood fromString(String s) {
            if (s == null || s.isBlank()) return NEUTRAL;
            for (Mood m : values()) {
                if (m.name().equalsIgnoreCase(s.trim())) return m;
            }
            throw new IllegalArgumentException("Unknown mood: " + s);
        }
    }

    public static final int MAX_PAIN_LEVEL   = 10;
    public static final double MAX_SLEEP_HOURS = 24.0;
    public static final int MAX_WATER_INTAKE = 30;

    private LocalDate entryDate;
    private Mood      mood = Mood.NEUTRAL;
    private String    symptoms;
    private int       painLevel   = 0;
    private double    sleepHours  = 0.0;
    private int       waterIntake = 0;

    /** Default constructor for DAO hydration. */
    public MoodEntry() {}

    public MoodEntry(int userId, LocalDate entryDate, Mood mood, String symptoms,
                     int painLevel, double sleepHours, int waterIntake) {
        setUserId(userId);
        setEntryDate(entryDate);
        setMood(mood != null ? mood : Mood.NEUTRAL);
        setSymptoms(symptoms);
        setPainLevel(painLevel);
        setSleepHours(sleepHours);
        setWaterIntake(waterIntake);
        setCreatedAt(LocalDateTime.now());
    }

    // ── WorkspaceItem contract ───────────────────────────────────────────

    @Override
    public void validate() {
        if (entryDate == null) {
            throw new IllegalArgumentException("Entry date is required.");
        }
        if (entryDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Entry date cannot be in the future.");
        }
        if (mood == null) {
            throw new IllegalArgumentException("Mood is required.");
        }
        if (painLevel < 0 || painLevel > MAX_PAIN_LEVEL) {
            throw new IllegalArgumentException("Pain level must be between 0 and " + MAX_PAIN_LEVEL + ".");
        }
        if (sleepHours < 0 || sleepHours > MAX_SLEEP_HOURS) {
            throw new IllegalArgumentException("Sleep hours must be between 0 and " + MAX_SLEEP_HOURS + ".");
        }
        if (waterIntake < 0 || waterIntake > MAX_WATER_INTAKE) {
            throw new IllegalArgumentException("Water intake must be between 0 and " + MAX_WATER_INTAKE + " glasses.");
        }
    }

    @Override
    public String summary() {
        return "[Mood] " + entryDate + " — " + mood + " (Pain: " + painLevel + "/10, Sleep: " +
               sleepHours + "h, Water: " + waterIntake + ")";
    }

    // ── Getters / validated setters ──────────────────────────────────────

    public LocalDate getEntryDate() {
        return entryDate;
    }

    public void setEntryDate(LocalDate entryDate) {
        if (entryDate == null) {
            throw new IllegalArgumentException("Entry date cannot be null.");
        }
        this.entryDate = entryDate;
    }

    public Mood getMood() {
        return mood;
    }

    public void setMood(Mood mood) {
        if (mood == null) {
            throw new IllegalArgumentException("Mood cannot be null.");
        }
        this.mood = mood;
    }

    public String getSymptoms() {
        return symptoms;
    }

    public void setSymptoms(String symptoms) {
        this.symptoms = symptoms != null ? symptoms.trim() : null;
    }

    public int getPainLevel() {
        return painLevel;
    }

    public void setPainLevel(int painLevel) {
        if (painLevel < 0 || painLevel > MAX_PAIN_LEVEL) {
            throw new IllegalArgumentException("Pain level must be between 0 and " + MAX_PAIN_LEVEL + ".");
        }
        this.painLevel = painLevel;
    }

    public double getSleepHours() {
        return sleepHours;
    }

    public void setSleepHours(double sleepHours) {
        if (sleepHours < 0 || sleepHours > MAX_SLEEP_HOURS) {
            throw new IllegalArgumentException("Sleep hours must be between 0 and " + MAX_SLEEP_HOURS + ".");
        }
        this.sleepHours = sleepHours;
    }

    public int getWaterIntake() {
        return waterIntake;
    }

    public void setWaterIntake(int waterIntake) {
        if (waterIntake < 0 || waterIntake > MAX_WATER_INTAKE) {
            throw new IllegalArgumentException("Water intake must be between 0 and " + MAX_WATER_INTAKE + " glasses.");
        }
        this.waterIntake = waterIntake;
    }

    @Override
    public String toString() {
        return "MoodEntry{id=" + getId() + ", userId=" + getUserId() +
               ", entryDate=" + entryDate + ", mood=" + mood +
               ", painLevel=" + painLevel + ", sleepHours=" + sleepHours +
               ", waterIntake=" + waterIntake + "}";
    }
}

