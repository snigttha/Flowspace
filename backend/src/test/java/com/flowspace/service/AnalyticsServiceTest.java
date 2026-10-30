package com.flowspace.service;

import com.flowspace.model.AnalyticsData.CycleStats;
import com.flowspace.model.AnalyticsData.MoodTrendPoint;
import com.flowspace.model.AnalyticsData.TaskStats;
import com.flowspace.model.MoodEntry;
import com.flowspace.model.MoodEntry.Mood;
import com.flowspace.model.PeriodLog;
import com.flowspace.model.Task;
import com.flowspace.model.Task.Priority;
import com.flowspace.model.Task.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AnalyticsService – Unit Tests")
class AnalyticsServiceTest {

    private AnalyticsService service;
    private final LocalDate referenceToday = LocalDate.of(2026, 10, 30);

    @BeforeEach
    void setUp() {
        service = new AnalyticsService();
    }

    // ── 1. Empty Dataset Tests ───────────────────────────────────────────

    @Nested
    @DisplayName("Empty Dataset Tests")
    class EmptyDatasetTests {

        @Test
        @DisplayName("computeMoodTrends handles null and empty lists safely")
        void testMoodTrendsEmpty() {
            List<MoodTrendPoint> fromNull = service.computeMoodTrends(null);
            assertNotNull(fromNull);
            assertTrue(fromNull.isEmpty());

            List<MoodTrendPoint> fromEmpty = service.computeMoodTrends(Collections.emptyList());
            assertNotNull(fromEmpty);
            assertTrue(fromEmpty.isEmpty());
        }

        @Test
        @DisplayName("computeCycleStats handles null and empty lists without fabricating dates")
        void testCycleStatsEmpty() {
            CycleStats fromNull = service.computeCycleStats(null);
            assertNotNull(fromNull);
            assertFalse(fromNull.isHasData());
            assertNull(fromNull.getAverageCycleLength());
            assertNull(fromNull.getPredictedNextDate());
            assertEquals(0, fromNull.getCycleCount());

            CycleStats fromEmpty = service.computeCycleStats(Collections.emptyList());
            assertNotNull(fromEmpty);
            assertFalse(fromEmpty.isHasData());
            assertNull(fromEmpty.getAverageCycleLength());
            assertNull(fromEmpty.getPredictedNextDate());
            assertEquals(0, fromEmpty.getCycleCount());
        }

        @Test
        @DisplayName("computeTaskStats handles null and empty tasks with 0% completion")
        void testTaskStatsEmpty() {
            TaskStats fromNull = service.computeTaskStats(null, referenceToday);
            assertNotNull(fromNull);
            assertEquals(0, fromNull.getTotal());
            assertEquals(0, fromNull.getCompleted());
            assertEquals(0, fromNull.getPending());
            assertEquals(0, fromNull.getCompletionPercentage());

            TaskStats fromEmpty = service.computeTaskStats(Collections.emptyList(), referenceToday);
            assertNotNull(fromEmpty);
            assertEquals(0, fromEmpty.getTotal());
            assertEquals(0, fromEmpty.getCompleted());
            assertEquals(0, fromEmpty.getPending());
            assertEquals(0, fromEmpty.getCompletionPercentage());
        }

        @Test
        @DisplayName("generateInsights handles null or empty inputs safely")
        void testInsightsEmpty() {
            List<String> insights = service.generateInsights(null, null, null);
            assertNotNull(insights);
            assertTrue(insights.isEmpty());

            TaskStats emptyTasks = new TaskStats(0, 0, 0, 0, 0, 0, 0);
            CycleStats emptyCycles = new CycleStats(false, null, 0, null, null, "No logs");
            List<String> insights2 = service.generateInsights(emptyTasks, emptyCycles, Collections.emptyList());
            assertNotNull(insights2);
            assertTrue(insights2.isEmpty());
        }
    }

    // ── 2. One-Record Dataset Tests ──────────────────────────────────────

    @Nested
    @DisplayName("One-Record Dataset Tests")
    class OneRecordDatasetTests {

        @Test
        @DisplayName("One completed task yields 100% completion")
        void testOneCompletedTask() {
            Task t = new Task(1, "Fix navigation", referenceToday.plusDays(2), Priority.HIGH, Status.DONE);
            TaskStats stats = service.computeTaskStats(List.of(t), referenceToday);

            assertEquals(1, stats.getTotal());
            assertEquals(1, stats.getCompleted());
            assertEquals(0, stats.getPending());
            assertEquals(100, stats.getCompletionPercentage());
            assertEquals(0, stats.getOverdue());
        }

        @Test
        @DisplayName("One pending task yields 0% completion")
        void testOnePendingTask() {
            Task t = new Task(1, "Fix navigation", referenceToday.plusDays(2), Priority.HIGH, Status.TODO);
            TaskStats stats = service.computeTaskStats(List.of(t), referenceToday);

            assertEquals(1, stats.getTotal());
            assertEquals(0, stats.getCompleted());
            assertEquals(1, stats.getPending());
            assertEquals(0, stats.getCompletionPercentage());
        }

        @Test
        @DisplayName("One cycle record uses entered cycle length for next prediction")
        void testOneCycleRecord() {
            LocalDate start = LocalDate.of(2026, 9, 20);
            PeriodLog log = new PeriodLog(1, start, 30);
            CycleStats stats = service.computeCycleStats(List.of(log));

            assertTrue(stats.isHasData());
            assertEquals(1, stats.getCycleCount());
            assertEquals(30, stats.getAverageCycleLength());
            assertEquals(start, stats.getLastPeriodDate());
            assertEquals(start.plusDays(30), stats.getPredictedNextDate());
        }

        @Test
        @DisplayName("One mood record yields a single trend point without fabricating correlation insights")
        void testOneMoodRecord() {
            LocalDate date = LocalDate.of(2026, 10, 25);
            MoodEntry entry = new MoodEntry(1, date, Mood.HAPPY, "Energetic", 0, 8.0, 6);
            List<MoodTrendPoint> points = service.computeMoodTrends(List.of(entry));

            assertEquals(1, points.size());
            assertEquals("HAPPY", points.get(0).getMood());
            assertEquals(5, points.get(0).getScore());
            assertEquals(date, points.get(0).getDate());

            // With only 1 mood entry (< 3), no premature mood insights should be generated
            List<String> insights = service.generateInsights(null, null, List.of(entry));
            assertTrue(insights.isEmpty());
        }
    }

    // ── 3. Multiple Records Tests ────────────────────────────────────────

    @Nested
    @DisplayName("Multiple Records Tests")
    class MultipleRecordsTests {

        @Test
        @DisplayName("Multiple tasks calculate counts, pending, overdue, and percentage correctly")
        void testMultipleTasks() {
            List<Task> tasks = new ArrayList<>();
            tasks.add(new Task(1, "Task 1", referenceToday.minusDays(3), Priority.HIGH, Status.TODO)); // overdue
            tasks.add(new Task(1, "Task 2", referenceToday.plusDays(1), Priority.MEDIUM, Status.IN_PROGRESS));
            tasks.add(new Task(1, "Task 3", referenceToday.plusDays(5), Priority.LOW, Status.DONE));
            tasks.add(new Task(1, "Task 4", referenceToday.plusDays(7), Priority.MEDIUM, Status.DONE));
            tasks.add(new Task(1, "Task 5", referenceToday.plusDays(10), Priority.HIGH, Status.TODO));

            TaskStats stats = service.computeTaskStats(tasks, referenceToday);

            assertEquals(5, stats.getTotal());
            assertEquals(2, stats.getCompleted());
            assertEquals(3, stats.getPending());
            assertEquals(2, stats.getTodo());
            assertEquals(1, stats.getInProgress());
            assertEquals(1, stats.getOverdue());
            assertEquals(40, stats.getCompletionPercentage()); // 2 of 5 = 40%
        }

        @Test
        @DisplayName("Multiple cycles calculate accurate average gap and chronological next prediction")
        void testMultipleCycles() {
            List<PeriodLog> logs = new ArrayList<>();
            // Unordered list to test sorting
            logs.add(new PeriodLog(1, LocalDate.of(2026, 8, 29), 28)); // gap 28
            logs.add(new PeriodLog(1, LocalDate.of(2026, 8, 1), 28));
            logs.add(new PeriodLog(1, LocalDate.of(2026, 9, 28), 28)); // gap 30

            CycleStats stats = service.computeCycleStats(logs);

            assertTrue(stats.isHasData());
            assertEquals(3, stats.getCycleCount());
            // gaps: 28 days (Aug 1 to Aug 29) + 30 days (Aug 29 to Sep 28) = 58 / 2 = 29 days
            assertEquals(29, stats.getAverageCycleLength());
            assertEquals(LocalDate.of(2026, 9, 28), stats.getLastPeriodDate());
            assertEquals(LocalDate.of(2026, 9, 28).plusDays(29), stats.getPredictedNextDate());
        }

        @Test
        @DisplayName("Multiple moods produce sorted chronological trend points and valid insights")
        void testMultipleMoodsAndInsights() {
            List<MoodEntry> moods = new ArrayList<>();
            moods.add(new MoodEntry(1, LocalDate.of(2026, 10, 1), Mood.HAPPY, "", 1, 8.0, 7));
            moods.add(new MoodEntry(1, LocalDate.of(2026, 10, 2), Mood.CALM, "", 0, 7.5, 8));
            moods.add(new MoodEntry(1, LocalDate.of(2026, 10, 3), Mood.TIRED, "", 3, 5.5, 5));
            moods.add(new MoodEntry(1, LocalDate.of(2026, 10, 4), Mood.SAD, "", 4, 6.0, 4));

            List<MoodTrendPoint> trends = service.computeMoodTrends(moods);
            assertEquals(4, trends.size());
            assertEquals(LocalDate.of(2026, 10, 1), trends.get(0).getDate());
            assertEquals(LocalDate.of(2026, 10, 4), trends.get(3).getDate());

            TaskStats tStats = new TaskStats(4, 2, 2, 1, 1, 0, 50);
            CycleStats cStats = new CycleStats(true, 28, 2, LocalDate.of(2026, 9, 20), LocalDate.of(2026, 10, 18), "OK");

            List<String> insights = service.generateInsights(tStats, cStats, moods);
            assertNotNull(insights);
            assertFalse(insights.isEmpty());

            // Check that task insight exists
            assertTrue(insights.stream().anyMatch(s -> s.contains("2 of 4 tasks are completed (50%)")));
            // Check that average wellness metrics insight exists
            assertTrue(insights.stream().anyMatch(s -> s.contains("wellness entries") && s.contains("hours of sleep")));
            // Check that sleep vs mood correlation insight exists
            assertTrue(insights.stream().anyMatch(s -> s.contains("7+ hours of sleep")));
        }
    }

    // ── 4. Edge Cases & Calculations ─────────────────────────────────────

    @Nested
    @DisplayName("Edge Cases and Boundary Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Percentage rounding handles 1/3 (33%) and 2/3 (67%) correctly")
        void testPercentageRounding() {
            List<Task> threeTasks = List.of(
                new Task(1, "Task 1", referenceToday, Priority.LOW, Status.DONE),
                new Task(1, "Task 2", referenceToday, Priority.LOW, Status.TODO),
                new Task(1, "Task 3", referenceToday, Priority.LOW, Status.TODO)
            );
            TaskStats s1 = service.computeTaskStats(threeTasks, referenceToday);
            assertEquals(33, s1.getCompletionPercentage());

            List<Task> threeTasksTwoDone = List.of(
                new Task(1, "Task 1", referenceToday, Priority.LOW, Status.DONE),
                new Task(1, "Task 2", referenceToday, Priority.LOW, Status.DONE),
                new Task(1, "Task 3", referenceToday, Priority.LOW, Status.TODO)
            );
            TaskStats s2 = service.computeTaskStats(threeTasksTwoDone, referenceToday);
            assertEquals(67, s2.getCompletionPercentage());
        }

        @Test
        @DisplayName("Cycle length clamping enforces 15 to 60 days limit")
        void testCycleLengthClamping() {
            // Test unusually short interval (< 15)
            PeriodLog p1 = new PeriodLog(1, LocalDate.of(2026, 8, 1), 28);
            PeriodLog p2 = new PeriodLog(1, LocalDate.of(2026, 8, 10), 28); // 9 day gap
            CycleStats statsShort = service.computeCycleStats(List.of(p1, p2));
            assertEquals(15, statsShort.getAverageCycleLength());

            // Test unusually long interval (> 60)
            PeriodLog p3 = new PeriodLog(1, LocalDate.of(2026, 5, 1), 28);
            PeriodLog p4 = new PeriodLog(1, LocalDate.of(2026, 8, 1), 28); // 92 day gap
            CycleStats statsLong = service.computeCycleStats(List.of(p3, p4));
            assertEquals(60, statsLong.getAverageCycleLength());
        }

        @Test
        @DisplayName("Task overdue calculation ignores completed tasks even if due date passed")
        void testOverdueIgnoresDone() {
            Task donePast = new Task(1, "Done Past", referenceToday.minusDays(10), Priority.HIGH, Status.DONE);
            TaskStats stats = service.computeTaskStats(List.of(donePast), referenceToday);
            assertEquals(0, stats.getOverdue());
            assertEquals(1, stats.getCompleted());
        }

        @Test
        @DisplayName("100% completion produces special celebratory insight")
        void testCelebratoryInsight() {
            TaskStats allDone = new TaskStats(3, 3, 0, 0, 0, 0, 100);
            List<String> insights = service.generateInsights(allDone, null, Collections.emptyList());
            assertEquals(1, insights.size());
            assertTrue(insights.get(0).contains("All 3 tasks are complete!"));
        }
    }
}

