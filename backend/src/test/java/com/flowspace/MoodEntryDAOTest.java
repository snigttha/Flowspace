package com.flowspace;

import com.flowspace.dao.MoodEntryDAO;
import com.flowspace.model.Mood;
import com.flowspace.model.MoodAverages;
import com.flowspace.model.MoodEntry;
import com.flowspace.util.DBConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for MoodEntryDAO using SQLite in-memory database.
 * Tests CRUD, Upsert, Aggregations, and User Isolation.
 */
public class MoodEntryDAOTest {
    private MoodEntryDAO dao;
    private Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        // Use unique in-memory database for isolation
        String testDb = "jdbc:sqlite::memory:";
        DBConnection.setCustomJdbcUrl(testDb);
        conn = DBConnection.getConnection();
        DBConnection.initializeSchema(conn);
        dao = new MoodEntryDAO();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
        DBConnection.setCustomJdbcUrl(null);
    }

    @Test
    @DisplayName("Create and find MoodEntry by date")
    void testCreateAndFind() {
        LocalDate date = LocalDate.of(2026, 10, 15);
        MoodEntry entry = new MoodEntry(0, 1, date, Mood.HAPPY, "Yoga morning", 1, 8.0, 7, LocalDateTime.now());
        
        MoodEntry created = dao.create(entry);
        assertTrue(created.getId() > 0);

        MoodEntry fetched = dao.findByDate(date, 1);
        assertNotNull(fetched);
        assertEquals(Mood.HAPPY, fetched.getMood());
        assertEquals("Yoga morning", fetched.getSymptoms());
        assertEquals(1, fetched.getPainLevel());
    }

    @Test
    @DisplayName("Upsert: saving on same date updates existing entry")
    void testUpsertOnSameDate() {
        LocalDate date = LocalDate.of(2026, 10, 16);
        
        // 1st entry on date
        MoodEntry entry1 = new MoodEntry(0, 1, date, Mood.TIRED, "Late sleep", 4, 5.5, 4, LocalDateTime.now());
        dao.upsert(entry1);

        // 2nd entry on the same date with updated mood & water
        MoodEntry entry2 = new MoodEntry(0, 1, date, Mood.CALM, "Took an afternoon nap", 2, 7.5, 8, LocalDateTime.now());
        MoodEntry updated = dao.upsert(entry2);

        // Should update the row rather than creating a duplicate
        List<MoodEntry> all = dao.findAll(1);
        assertEquals(1, all.size(), "Only one entry should exist for the date");

        MoodEntry current = dao.findByDate(date, 1);
        assertNotNull(current);
        assertEquals(Mood.CALM, current.getMood());
        assertEquals("Took an afternoon nap", current.getSymptoms());
        assertEquals(2, current.getPainLevel());
        assertEquals(7.5, current.getSleepHours());
        assertEquals(8, current.getWaterIntake());
    }

    @Test
    @DisplayName("Fetch last N entries returns ordered descending")
    void testFindRecent() {
        dao.create(new MoodEntry(0, 1, LocalDate.of(2026, 10, 10), Mood.SAD, "", 5, 6.0, 4, null));
        dao.create(new MoodEntry(0, 1, LocalDate.of(2026, 10, 11), Mood.NEUTRAL, "", 3, 7.0, 5, null));
        dao.create(new MoodEntry(0, 1, LocalDate.of(2026, 10, 12), Mood.HAPPY, "", 1, 8.5, 9, null));

        List<MoodEntry> recent = dao.findRecent(1, 2);
        assertEquals(2, recent.size());
        assertEquals(LocalDate.of(2026, 10, 12), recent.get(0).getEntryDate());
        assertEquals(LocalDate.of(2026, 10, 11), recent.get(1).getEntryDate());
    }

    @Test
    @DisplayName("Fetch entries between two dates")
    void testFindBetweenDates() {
        dao.create(new MoodEntry(0, 1, LocalDate.of(2026, 10, 1), Mood.HAPPY, "", 0, 8.0, 8, null));
        dao.create(new MoodEntry(0, 1, LocalDate.of(2026, 10, 5), Mood.CALM, "", 1, 7.0, 6, null));
        dao.create(new MoodEntry(0, 1, LocalDate.of(2026, 10, 10), Mood.TIRED, "", 3, 6.0, 5, null));

        List<MoodEntry> range = dao.findBetweenDates(1, LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 8));
        assertEquals(1, range.size());
        assertEquals(LocalDate.of(2026, 10, 5), range.get(0).getEntryDate());
    }

    @Test
    @DisplayName("Compute averages for sleep, water, and pain (Member 6 requirement)")
    void testAverages() {
        dao.create(new MoodEntry(0, 1, LocalDate.of(2026, 10, 1), Mood.HAPPY, "", 2, 8.0, 6, null));
        dao.create(new MoodEntry(0, 1, LocalDate.of(2026, 10, 2), Mood.CALM, "", 4, 6.0, 10, null));

        MoodAverages avg = dao.getAverages(1);
        assertEquals(2, avg.getTotalEntries());
        assertEquals(7.0, avg.getAvgSleep(), 0.05); // (8 + 6) / 2 = 7.0
        assertEquals(8.0, avg.getAvgWater(), 0.05); // (6 + 10) / 2 = 8.0
        assertEquals(3.0, avg.getAvgPain(), 0.05);  // (2 + 4) / 2 = 3.0
    }

    @Test
    @DisplayName("Tenant isolation: User 1 cannot access User 2's mood entries")
    void testUserIsolation() {
        LocalDate date = LocalDate.of(2026, 10, 18);
        dao.create(new MoodEntry(0, 1, date, Mood.HAPPY, "Secret note 1", 0, 8.0, 8, null));
        dao.create(new MoodEntry(0, 2, date, Mood.SAD, "Secret note 2", 5, 4.0, 3, null));

        // User 1 lookup
        MoodEntry u1Entry = dao.findByDate(date, 1);
        assertNotNull(u1Entry);
        assertEquals(Mood.HAPPY, u1Entry.getMood());

        // User 2 cannot see User 1's entry
        MoodEntry u2Entry = dao.findByDate(date, 2);
        assertNotNull(u2Entry);
        assertEquals(Mood.SAD, u2Entry.getMood());

        // User 1 cannot delete User 2's entry
        boolean deleted = dao.delete(u2Entry.getId(), 1);
        assertFalse(deleted);
        assertNotNull(dao.findById(u2Entry.getId(), 2));
    }
}
