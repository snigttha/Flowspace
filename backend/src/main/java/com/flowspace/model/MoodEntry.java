package com.flowspace.model;

import com.flowspace.util.ValidationException;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Concrete entity representing a daily wellness & mood check-in.
 * Inherits from WorkspaceItem (OOP Inheritance).
 * Enforces strict encapsulation and domain validation.
 */
public class MoodEntry extends WorkspaceItem {
    private LocalDate entryDate;
    private Mood mood;
    private String symptoms;
    private int painLevel;      // 0 to 10
    private double sleepHours;  // 0.0 to 24.0
    private int waterIntake;    // 0 to 30 glasses

    public MoodEntry() {
        super();
        this.entryDate = LocalDate.now();
        this.painLevel = 0;
        this.sleepHours = 7.0;
        this.waterIntake = 6;
    }

    public MoodEntry(int id, int userId, LocalDate entryDate, Mood mood, 
                     String symptoms, int painLevel, double sleepHours, 
                     int waterIntake, LocalDateTime createdAt) {
        super(id, userId, createdAt);
        setEntryDate(entryDate);
        setMood(mood);
        setSymptoms(symptoms);
        setPainLevel(painLevel);
        setSleepHours(sleepHours);
        setWaterIntake(waterIntake);
    }

    // Encapsulation: validated getters and setters
    public LocalDate getEntryDate() {
        return entryDate;
    }

    public void setEntryDate(LocalDate entryDate) {
        if (entryDate == null) {
            throw new ValidationException("entryDate", "Date is required.");
        }
        if (entryDate.isAfter(LocalDate.now())) {
            throw new ValidationException("entryDate", "Date cannot be in the future.");
        }
        this.entryDate = entryDate;
    }

    public Mood getMood() {
        return mood;
    }

    public void setMood(Mood mood) {
        if (mood == null) {
            throw new ValidationException("mood", "Mood is required.");
        }
        this.mood = mood;
    }

    public String getSymptoms() {
        return symptoms;
    }

    public void setSymptoms(String symptoms) {
        if (symptoms != null && symptoms.length() > 200) {
            throw new ValidationException("symptoms", "Symptoms cannot exceed 200 characters.");
        }
        this.symptoms = symptoms != null ? symptoms.trim() : null;
    }

    public int getPainLevel() {
        return painLevel;
    }

    public void setPainLevel(int painLevel) {
        if (painLevel < 0 || painLevel > 10) {
            throw new ValidationException("painLevel", "Pain level must be between 0 and 10.");
        }
        this.painLevel = painLevel;
    }

    public double getSleepHours() {
        return sleepHours;
    }

    public void setSleepHours(double sleepHours) {
        if (sleepHours < 0.0 || sleepHours > 24.0) {
            throw new ValidationException("sleepHours", "Sleep hours must be between 0 and 24.");
        }
        this.sleepHours = sleepHours;
    }

    public int getWaterIntake() {
        return waterIntake;
    }

    public void setWaterIntake(int waterIntake) {
        if (waterIntake < 0 || waterIntake > 30) {
            throw new ValidationException("waterIntake", "Water intake must be between 0 and 30 glasses.");
        }
        this.waterIntake = waterIntake;
    }

    /**
     * Enforces complete entity validation rules before persisting to database.
     */
    @Override
    public void validate() throws ValidationException {
        if (entryDate == null) {
            throw new ValidationException("entryDate", "Date is required.");
        }
        if (entryDate.isAfter(LocalDate.now())) {
            throw new ValidationException("entryDate", "Date cannot be in the future.");
        }
        if (mood == null) {
            throw new ValidationException("mood", "Mood is required.");
        }
        if (painLevel < 0 || painLevel > 10) {
            throw new ValidationException("painLevel", "Pain level must be between 0 and 10.");
        }
        if (sleepHours < 0.0 || sleepHours > 24.0) {
            throw new ValidationException("sleepHours", "Sleep hours must be between 0 and 24.");
        }
        if (waterIntake < 0 || waterIntake > 30) {
            throw new ValidationException("waterIntake", "Water intake must be between 0 and 30 glasses.");
        }
        if (symptoms != null && symptoms.length() > 200) {
            throw new ValidationException("symptoms", "Symptoms cannot exceed 200 characters.");
        }
    }

    /**
     * Polymorphic implementation of summary() for global search & activity.
     */
    @Override
    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Mood Entry (").append(entryDate).append("): ")
          .append(mood != null ? mood.getDisplayName() : "Unknown")
          .append(" | Sleep: ").append(String.format("%.1f", sleepHours)).append("h")
          .append(" | Water: ").append(waterIntake).append(" cups")
          .append(" | Pain: ").append(painLevel).append("/10");
        if (symptoms != null && !symptoms.isEmpty()) {
            sb.append(" | Symptoms: ").append(symptoms);
        }
        return sb.toString();
    }

    @Override
    public String getItemType() {
        return "MOOD";
    }

    @Override
    public String toString() {
        return summary();
    }
}
