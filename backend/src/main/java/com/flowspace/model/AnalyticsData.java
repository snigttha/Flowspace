package com.flowspace.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Data containers for FlowSpace Analytics and Dashboard metrics.
 * Strongly-typed, testable, and directly serializable to JSON.
 *
 * @author Member 6 (Workspace & Integration Lead)
 */
public class AnalyticsData {

    private int userId;
    private DashboardCounts counts;
    private TaskStats taskStats;
    private CycleStats cycleStats;
    private List<MoodTrendPoint> moodTrends = new ArrayList<>();
    private List<RecentActivity> recentActivity = new ArrayList<>();
    private List<String> insights = new ArrayList<>();

    public AnalyticsData() {}

    public AnalyticsData(int userId) {
        this.userId = userId;
    }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public DashboardCounts getCounts() { return counts; }
    public void setCounts(DashboardCounts counts) { this.counts = counts; }

    public TaskStats getTaskStats() { return taskStats; }
    public void setTaskStats(TaskStats taskStats) { this.taskStats = taskStats; }

    public CycleStats getCycleStats() { return cycleStats; }
    public void setCycleStats(CycleStats cycleStats) { this.cycleStats = cycleStats; }

    public List<MoodTrendPoint> getMoodTrends() { return moodTrends; }
    public void setMoodTrends(List<MoodTrendPoint> moodTrends) {
        this.moodTrends = moodTrends != null ? moodTrends : new ArrayList<>();
    }

    public List<RecentActivity> getRecentActivity() { return recentActivity; }
    public void setRecentActivity(List<RecentActivity> recentActivity) {
        this.recentActivity = recentActivity != null ? recentActivity : new ArrayList<>();
    }

    public List<String> getInsights() { return insights; }
    public void setInsights(List<String> insights) {
        this.insights = insights != null ? insights : new ArrayList<>();
    }

    // ── Nested DTOs ──────────────────────────────────────────────────────

    public static class DashboardCounts {
        private int notes;
        private int tasks;
        private int openTasks;
        private int moods;
        private int cycles;

        public DashboardCounts() {}

        public DashboardCounts(int notes, int tasks, int openTasks, int moods, int cycles) {
            this.notes = notes;
            this.tasks = tasks;
            this.openTasks = openTasks;
            this.moods = moods;
            this.cycles = cycles;
        }

        public int getNotes() { return notes; }
        public void setNotes(int notes) { this.notes = notes; }

        public int getTasks() { return tasks; }
        public void setTasks(int tasks) { this.tasks = tasks; }

        public int getOpenTasks() { return openTasks; }
        public void setOpenTasks(int openTasks) { this.openTasks = openTasks; }

        public int getMoods() { return moods; }
        public void setMoods(int moods) { this.moods = moods; }

        public int getCycles() { return cycles; }
        public void setCycles(int cycles) { this.cycles = cycles; }
    }

    public static class TaskStats {
        private int total;
        private int completed;
        private int pending;
        private int todo;
        private int inProgress;
        private int overdue;
        private int completionPercentage;

        public TaskStats() {}

        public TaskStats(int total, int completed, int pending, int todo, int inProgress,
                         int overdue, int completionPercentage) {
            this.total = total;
            this.completed = completed;
            this.pending = pending;
            this.todo = todo;
            this.inProgress = inProgress;
            this.overdue = overdue;
            this.completionPercentage = completionPercentage;
        }

        public int getTotal() { return total; }
        public void setTotal(int total) { this.total = total; }

        public int getCompleted() { return completed; }
        public void setCompleted(int completed) { this.completed = completed; }

        public int getPending() { return pending; }
        public void setPending(int pending) { this.pending = pending; }

        public int getTodo() { return todo; }
        public void setTodo(int todo) { this.todo = todo; }

        public int getInProgress() { return inProgress; }
        public void setInProgress(int inProgress) { this.inProgress = inProgress; }

        public int getOverdue() { return overdue; }
        public void setOverdue(int overdue) { this.overdue = overdue; }

        public int getCompletionPercentage() { return completionPercentage; }
        public void setCompletionPercentage(int completionPercentage) {
            this.completionPercentage = completionPercentage;
        }
    }

    public static class CycleStats {
        private boolean hasData;
        private Integer averageCycleLength;
        private int cycleCount;
        private LocalDate lastPeriodDate;
        private LocalDate predictedNextDate;
        private String statusMessage;

        public CycleStats() {}

        public CycleStats(boolean hasData, Integer averageCycleLength, int cycleCount,
                          LocalDate lastPeriodDate, LocalDate predictedNextDate, String statusMessage) {
            this.hasData = hasData;
            this.averageCycleLength = averageCycleLength;
            this.cycleCount = cycleCount;
            this.lastPeriodDate = lastPeriodDate;
            this.predictedNextDate = predictedNextDate;
            this.statusMessage = statusMessage;
        }

        public boolean isHasData() { return hasData; }
        public void setHasData(boolean hasData) { this.hasData = hasData; }

        public Integer getAverageCycleLength() { return averageCycleLength; }
        public void setAverageCycleLength(Integer averageCycleLength) {
            this.averageCycleLength = averageCycleLength;
        }

        public int getCycleCount() { return cycleCount; }
        public void setCycleCount(int cycleCount) { this.cycleCount = cycleCount; }

        public LocalDate getLastPeriodDate() { return lastPeriodDate; }
        public void setLastPeriodDate(LocalDate lastPeriodDate) {
            this.lastPeriodDate = lastPeriodDate;
        }

        public LocalDate getPredictedNextDate() { return predictedNextDate; }
        public void setPredictedNextDate(LocalDate predictedNextDate) {
            this.predictedNextDate = predictedNextDate;
        }

        public String getStatusMessage() { return statusMessage; }
        public void setStatusMessage(String statusMessage) {
            this.statusMessage = statusMessage;
        }
    }

    public static class MoodTrendPoint {
        private LocalDate date;
        private String mood;
        private int score;
        private int painLevel;
        private double sleepHours;
        private int waterIntake;

        public MoodTrendPoint() {}

        public MoodTrendPoint(LocalDate date, String mood, int score,
                              int painLevel, double sleepHours, int waterIntake) {
            this.date = date;
            this.mood = mood;
            this.score = score;
            this.painLevel = painLevel;
            this.sleepHours = sleepHours;
            this.waterIntake = waterIntake;
        }

        public LocalDate getDate() { return date; }
        public void setDate(LocalDate date) { this.date = date; }

        public String getMood() { return mood; }
        public void setMood(String mood) { this.mood = mood; }

        public int getScore() { return score; }
        public void setScore(int score) { this.score = score; }

        public int getPainLevel() { return painLevel; }
        public void setPainLevel(int painLevel) { this.painLevel = painLevel; }

        public double getSleepHours() { return sleepHours; }
        public void setSleepHours(double sleepHours) { this.sleepHours = sleepHours; }

        public int getWaterIntake() { return waterIntake; }
        public void setWaterIntake(int waterIntake) { this.waterIntake = waterIntake; }
    }

    public static class RecentActivity {
        private String type;
        private String title;
        private LocalDateTime timestamp;
        private String formattedDate;

        public RecentActivity() {}

        public RecentActivity(String type, String title, LocalDateTime timestamp, String formattedDate) {
            this.type = type;
            this.title = title;
            this.timestamp = timestamp;
            this.formattedDate = formattedDate;
        }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

        public String getFormattedDate() { return formattedDate; }
        public void setFormattedDate(String formattedDate) { this.formattedDate = formattedDate; }
    }
}

