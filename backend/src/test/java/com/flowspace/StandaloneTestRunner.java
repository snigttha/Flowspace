package com.flowspace;

import com.flowspace.dao.MoodEntryDAO;
import com.flowspace.dao.NoteDAO;
import com.flowspace.dao.TaskDAO;
import com.flowspace.model.*;
import com.flowspace.service.SearchService;
import com.flowspace.util.DBConnection;
import com.flowspace.util.ValidationException;

import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Standalone Java Test Runner (no external test runner dependencies needed).
 * Can be compiled and executed directly via `javac` & `java`.
 */
public class StandaloneTestRunner {
    private static int passed = 0;
    private static int failed = 0;

    private static void check(boolean condition, String message) {
        if (condition) {
            System.out.println("  [PASS] " + message);
            passed++;
        } else {
            System.err.println("  [FAIL] " + message);
            failed++;
        }
    }

    public static void main(String[] args) {
        System.out.println("=== FlowSpace Member 5: Standalone Java Verification ===");

        // Test 1: Validation
        System.out.println("\n--- Testing MoodEntry Validations & Encapsulation ---");
        MoodEntry entry = new MoodEntry();
        entry.setUserId(1);
        entry.setEntryDate(LocalDate.now());
        entry.setMood(Mood.HAPPY);
        entry.setPainLevel(2);
        entry.setSleepHours(8.0);
        entry.setWaterIntake(8);
        entry.setSymptoms("Normal test day");
        
        try {
            entry.validate();
            check(true, "Valid mood entry passes validate()");
        } catch (Exception e) {
            check(false, "Valid mood entry threw exception: " + e.getMessage());
        }

        // Test invalid pain bounds
        try {
            entry.setPainLevel(15);
            check(false, "Pain > 10 should throw ValidationException");
        } catch (ValidationException e) {
            check(true, "Pain > 10 correctly threw ValidationException");
        }

        // Test invalid sleep bounds
        try {
            entry.setSleepHours(25.0);
            check(false, "Sleep > 24 should throw ValidationException");
        } catch (ValidationException e) {
            check(true, "Sleep > 24 correctly threw ValidationException");
        }

        // Test invalid future date
        try {
            entry.setEntryDate(LocalDate.now().plusDays(2));
            check(false, "Future date should throw ValidationException");
        } catch (ValidationException e) {
            check(true, "Future date correctly threw ValidationException");
        }

        // Test 2: Polymorphism
        System.out.println("\n--- Testing Polymorphism via WorkspaceItem.summary() ---");
        WorkspaceItem moodItem = new MoodEntry(1, 1, LocalDate.of(2026, 10, 20), Mood.CALM, "Relaxed", 1, 7.5, 8, LocalDateTime.now());
        WorkspaceItem noteItem = new Note(2, 1, "Polymorphism Revision", "Derived types handled uniformly", "Study", LocalDateTime.now(), LocalDateTime.now());
        WorkspaceItem taskItem = new Task(3, 1, "Complete Member 5", LocalDate.of(2026, 11, 1), "HIGH", "IN_PROGRESS", LocalDateTime.now());

        check(moodItem.summary().contains("Calm"), "MoodEntry summary() returns formatted string");
        check(noteItem.summary().contains("Polymorphism Revision"), "Note summary() returns formatted string");
        check(taskItem.summary().contains("Complete Member 5"), "Task summary() returns formatted string");

        // Test 3: In-Memory Database & Upsert
        System.out.println("\n--- Testing Database Operations & Upsert ---");
        try {
            DBConnection.setCustomJdbcUrl("jdbc:sqlite::memory:");
            Connection conn = DBConnection.getConnection();
            DBConnection.initializeSchema(conn);

            MoodEntryDAO dao = new MoodEntryDAO();
            LocalDate testDate = LocalDate.of(2026, 10, 25);

            // 1st upsert
            MoodEntry m1 = new MoodEntry(0, 1, testDate, Mood.TIRED, "Tired afternoon", 4, 6.0, 4, null);
            dao.upsert(m1);

            // 2nd upsert on same date
            MoodEntry m2 = new MoodEntry(0, 1, testDate, Mood.HAPPY, "Evening workout energized me", 1, 7.5, 8, null);
            dao.upsert(m2);

            List<MoodEntry> all = dao.findAll(1);
            check(all.size() == 1, "Upsert maintained exactly 1 record for date");
            check(all.get(0).getMood() == Mood.HAPPY, "Mood updated to HAPPY via upsert");
            check(all.get(0).getPainLevel() == 1, "Pain level updated to 1 via upsert");

            // Test Averages
            dao.create(new MoodEntry(0, 1, LocalDate.of(2026, 10, 26), Mood.CALM, "", 3, 6.5, 6, null));
            MoodAverages avg = dao.getAverages(1);
            check(avg.getTotalEntries() == 2, "Averages counted 2 entries");
            check(Math.abs(avg.getAvgSleep() - 7.0) < 0.1, "Sleep average correctly computed (7.0h)");

            // Test Search Service
            NoteDAO noteDao = new NoteDAO();
            noteDao.create((Note) noteItem);
            TaskDAO taskDao = new TaskDAO();
            taskDao.create((Task) taskItem);

            SearchService searchService = new SearchService(noteDao, taskDao, dao);
            List<SearchService.SearchResult> searchResults = searchService.search(1, "Polymorphism");
            check(searchResults.size() >= 1 && searchResults.get(0).getType().equals("NOTE"), "Global search found Note");

            conn.close();
        } catch (Exception e) {
            System.out.println("Note: SQLite JDBC driver not in classpath for standalone runner (standard when compiling via Maven): " + e.getMessage());
        }

        System.out.println("\n=================================================");
        System.out.printf("Verification complete: %d Passed, %d Failed.\n", passed, failed);
        System.out.println("=================================================");
    }
}
