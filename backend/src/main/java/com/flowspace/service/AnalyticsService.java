package com.flowspace.service;

import com.flowspace.model.*;
import com.flowspace.model.AnalyticsData.*;
import com.flowspace.model.MoodEntry.Mood;
import com.flowspace.model.Task.Status;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Service for computing analytics metrics, mood trends, cycle statistics,
 * task completion rates, and personalized written insights.
 *
 * <p>Separates calculation and business rules from presentation and database access.
 * Handles missing dates, zero-record sets, and edge cases safely.
 *
 * @author Member 6 (Workspace & Integration Lead)
 */
public class AnalyticsService {

    public static final int MIN_ENTRIES_FOR_MOOD_INSIGHTS = 3;
    public static final double GOOD_SLEEP_THRESHOLD_HOURS = 7.0;

    /**
     * Aggregates and transforms mood records over time, sorted chronologically.
     * Maps each mood to its numeric score (1 to 5) based on the project's scale.
     *
     * @param moods raw list of mood entries (may be null or empty)
     * @return chronological list of trend points suitable for charts
     */
    public List<MoodTrendPoint> computeMoodTrends(List<MoodEntry> moods) {
        if (moods == null || moods.isEmpty()) {
            return Collections.emptyList();
        }

        List<MoodEntry> sorted = new ArrayList<>(moods);
        sorted.sort(Comparator.comparing(MoodEntry::getEntryDate));

        List<MoodTrendPoint> points = new ArrayList<>(sorted.size());
        for (MoodEntry m : sorted) {
            if (m.getEntryDate() == null || m.getMood() == null) continue;
            points.add(new MoodTrendPoint(
                m.getEntryDate(),
                m.getMood().name(),
                m.getMood().getScore(),
                m.getPainLevel(),
                m.getSleepHours(),
                m.getWaterIntake()
            ));
        }
        return points;
    }

    /**
     * Calculates cycle statistics from actual recorded period logs.
     *
     * <ul>
     *   <li>0 logs: {@code hasData = false}, no prediction or fabricated dates.</li>
     *   <li>1 log: uses entered cycle length for the next period prediction.</li>
     *   <li>>= 2 logs: calculates average gap between start dates, clamped to 15..60 days.</li>
     * </ul>
     *
     * @param cycles raw list of cycle records (may be null or empty)
     * @return structured CycleStats with verified dates and averages
     */
    public CycleStats computeCycleStats(List<PeriodLog> cycles) {
        if (cycles == null || cycles.isEmpty()) {
            return new CycleStats(false, null, 0, null, null, "No cycle logs recorded yet.");
        }

        List<PeriodLog> sorted = new ArrayList<>(cycles);
        sorted.sort(Comparator.comparing(PeriodLog::getCycleStartDate));

        int count = sorted.size();
        PeriodLog latest = sorted.get(count - 1);
        LocalDate lastDate = latest.getCycleStartDate();

        if (count == 1) {
            int length = Math.min(PeriodLog.MAX_CYCLE_LENGTH,
                         Math.max(PeriodLog.MIN_CYCLE_LENGTH, latest.getCycleLength()));
            LocalDate nextDate = lastDate.plusDays(length);
            return new CycleStats(
                true,
                length,
                1,
                lastDate,
                nextDate,
                "1 cycle logged. Prediction based on entered cycle length."
            );
        }

        // 2 or more logs: calculate real average gap between logged start dates
        long totalGapDays = 0;
        int gapCount = 0;
        for (int i = 0; i < count - 1; i++) {
            LocalDate d1 = sorted.get(i).getCycleStartDate();
            LocalDate d2 = sorted.get(i + 1).getCycleStartDate();
            long days = ChronoUnit.DAYS.between(d1, d2);
            if (days > 0) {
                totalGapDays += days;
                gapCount++;
            }
        }

        int avgGap;
        if (gapCount > 0) {
            avgGap = (int) Math.round((double) totalGapDays / gapCount);
        } else {
            avgGap = latest.getCycleLength();
        }

        // Clamp to allowed range (15 - 60 days)
        avgGap = Math.min(PeriodLog.MAX_CYCLE_LENGTH, Math.max(PeriodLog.MIN_CYCLE_LENGTH, avgGap));
        LocalDate nextDate = lastDate.plusDays(avgGap);

        String msg = "Average cycle: " + avgGap + " days based on " + count + " logged cycle(s).";
        return new CycleStats(true, avgGap, count, lastDate, nextDate, msg);
    }

    /**
     * Calculates task statistics including total, completed, pending,
     * overdue, and completion percentage.
     *
     * @param tasks list of tasks for the user (may be null or empty)
     * @param today current reference date for checking overdue tasks
     * @return structured TaskStats
     */
    public TaskStats computeTaskStats(List<Task> tasks, LocalDate today) {
        if (tasks == null || tasks.isEmpty()) {
            return new TaskStats(0, 0, 0, 0, 0, 0, 0);
        }

        if (today == null) {
            today = LocalDate.now();
        }

        int total = tasks.size();
        int completed = 0;
        int todo = 0;
        int inProgress = 0;
        int overdue = 0;

        for (Task t : tasks) {
            if (t.getStatus() == Status.DONE) {
                completed++;
            } else {
                if (t.getStatus() == Status.TODO) {
                    todo++;
                } else if (t.getStatus() == Status.IN_PROGRESS) {
                    inProgress++;
                }
                if (t.getDueDate() != null && t.getDueDate().isBefore(today)) {
                    overdue++;
                }
            }
        }

        int pending = todo + inProgress;
        int percentage = total > 0 ? Math.round((float) completed / total * 100.0f) : 0;
        percentage = Math.min(100, Math.max(0, percentage));

        return new TaskStats(total, completed, pending, todo, inProgress, overdue, percentage);
    }

    /** Overload using LocalDate.now(). */
    public TaskStats computeTaskStats(List<Task> tasks) {
        return computeTaskStats(tasks, LocalDate.now());
    }

    /**
     * Generates written insights based on computed task, cycle, and mood statistics.
     * Produces insights ONLY when valid and sufficient data exists. Never invents
     * medical advice or false conclusions.
     *
     * @param taskStats computed task stats
     * @param cycleStats computed cycle stats
     * @param moods list of mood entries
     * @return list of written insight strings
     */
    public List<String> generateInsights(TaskStats taskStats, CycleStats cycleStats, List<MoodEntry> moods) {
        List<String> insights = new ArrayList<>();

        // 1. Task insights
        if (taskStats != null && taskStats.getTotal() > 0) {
            if (taskStats.getCompleted() == taskStats.getTotal()) {
                insights.add("All " + taskStats.getTotal() + " tasks are complete! Outstanding progress.");
            } else {
                insights.add(taskStats.getCompleted() + " of " + taskStats.getTotal() +
                             " tasks are completed (" + taskStats.getCompletionPercentage() + "%).");
            }
            if (taskStats.getOverdue() > 0) {
                insights.add("You have " + taskStats.getOverdue() +
                             " overdue task" + (taskStats.getOverdue() > 1 ? "s" : "") +
                             " requiring attention.");
            }
        }

        // 2. Mood & Wellness insights (requires at least 3 entries to identify trends)
        if (moods != null && moods.size() >= MIN_ENTRIES_FOR_MOOD_INSIGHTS) {
            double totalSleep = 0;
            double totalWater = 0;
            double totalPain = 0;
            int count = moods.size();

            List<MoodEntry> goodSleep = new ArrayList<>();
            List<MoodEntry> shortSleep = new ArrayList<>();

            for (MoodEntry m : moods) {
                totalSleep += m.getSleepHours();
                totalWater += m.getWaterIntake();
                totalPain  += m.getPainLevel();

                if (m.getSleepHours() >= GOOD_SLEEP_THRESHOLD_HOURS) {
                    goodSleep.add(m);
                } else {
                    shortSleep.add(m);
                }
            }

            double avgSleep = totalSleep / count;
            double avgWater = totalWater / count;
            double avgPain  = totalPain / count;

            insights.add(String.format(Locale.US,
                "Across %d wellness entries, you average %.1f hours of sleep, %.1f glasses of water, and pain level %.1f/10.",
                count, avgSleep, avgWater, avgPain));

            // Sleep vs mood correlation insight if data has variance in sleep
            if (!goodSleep.isEmpty() && !shortSleep.isEmpty()) {
                double avgMoodGood = goodSleep.stream().mapToInt(m -> m.getMood().getScore()).average().orElse(0);
                double avgMoodShort = shortSleep.stream().mapToInt(m -> m.getMood().getScore()).average().orElse(0);

                insights.add(String.format(Locale.US,
                    "Your mood score averages %.1f/5 on days with %.0f+ hours of sleep versus %.1f/5 on shorter nights.",
                    avgMoodGood, GOOD_SLEEP_THRESHOLD_HOURS, avgMoodShort));
            }
        }

        // 3. Cycle insights
        if (cycleStats != null && cycleStats.isHasData() && cycleStats.getAverageCycleLength() != null) {
            String cycleMsg = "Average cycle length is " + cycleStats.getAverageCycleLength() +
                              " days across " + cycleStats.getCycleCount() + " logged cycle" +
                              (cycleStats.getCycleCount() > 1 ? "s" : "") + ".";
            if (cycleStats.getPredictedNextDate() != null) {
                cycleMsg += " Next period expected around " + cycleStats.getPredictedNextDate() + ".";
            }
            insights.add(cycleMsg);
        }

        return insights;
    }
}

