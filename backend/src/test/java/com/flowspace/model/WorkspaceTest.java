package com.flowspace.model;

import com.flowspace.dao.*;
import com.flowspace.model.AnalyticsData.DashboardCounts;
import com.flowspace.model.AnalyticsData.TaskStats;
import com.flowspace.model.MoodEntry.Mood;
import com.flowspace.model.Note.Category;
import com.flowspace.model.Task.Priority;
import com.flowspace.model.Task.Status;
import com.flowspace.service.AnalyticsService;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Workspace – Integration and User Privacy Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WorkspaceTest {

    private static final String MEM_URL = "jdbc:sqlite:file:workspacemem?mode=memory&cache=shared";
    private static Connection sharedConn;

    private static final int USER_1 = 101;
    private static final int USER_2 = 102;

    static class TestNoteDAO extends NoteDAO {
        @Override protected Connection openConnection() throws SQLException {
            return DriverManager.getConnection(MEM_URL);
        }
    }

    static class TestTaskDAO extends TaskDAO {
        @Override protected Connection openConnection() throws SQLException {
            return DriverManager.getConnection(MEM_URL);
        }
    }

    static class TestPeriodDAO extends PeriodLogDAO {
        @Override protected Connection openConnection() throws SQLException {
            return DriverManager.getConnection(MEM_URL);
        }
    }

    static class TestMoodDAO extends MoodEntryDAO {
        @Override protected Connection openConnection() throws SQLException {
            return DriverManager.getConnection(MEM_URL);
        }
    }

    private Workspace workspace1;
    private Workspace workspace2;

    @BeforeAll
    static void initDb() throws Exception {
        sharedConn = DriverManager.getConnection(MEM_URL);
        try (Statement st = sharedConn.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS notes (
                  note_id    INTEGER PRIMARY KEY AUTOINCREMENT,
                  user_id    INTEGER NOT NULL,
                  title      TEXT NOT NULL,
                  content    TEXT NOT NULL,
                  category   TEXT NOT NULL DEFAULT 'General',
                  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS tasks (
                  task_id    INTEGER PRIMARY KEY AUTOINCREMENT,
                  user_id    INTEGER NOT NULL,
                  title      TEXT NOT NULL,
                  due_date   TEXT NOT NULL,
                  priority   TEXT NOT NULL DEFAULT 'MEDIUM',
                  status     TEXT NOT NULL DEFAULT 'TODO',
                  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS period_logs (
                  log_id              INTEGER PRIMARY KEY AUTOINCREMENT,
                  user_id             INTEGER NOT NULL,
                  cycle_start_date    TEXT NOT NULL,
                  cycle_length        INTEGER NOT NULL DEFAULT 28,
                  predicted_next_date TEXT,
                  UNIQUE (user_id, cycle_start_date)
                )""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS mood_entries (
                  entry_id     INTEGER PRIMARY KEY AUTOINCREMENT,
                  user_id      INTEGER NOT NULL,
                  entry_date   TEXT NOT NULL,
                  mood         TEXT NOT NULL,
                  symptoms     TEXT,
                  pain_level   INTEGER NOT NULL DEFAULT 0,
                  sleep_hours  REAL NOT NULL DEFAULT 0,
                  water_intake INTEGER NOT NULL DEFAULT 0,
                  UNIQUE (user_id, entry_date)
                )""");
        }
    }

    @AfterAll
    static void tearDownDb() throws Exception {
        if (sharedConn != null && !sharedConn.isClosed()) {
            sharedConn.close();
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        // Clean tables before each test
        try (Statement st = sharedConn.createStatement()) {
            st.execute("DELETE FROM notes");
            st.execute("DELETE FROM tasks");
            st.execute("DELETE FROM period_logs");
            st.execute("DELETE FROM mood_entries");
        }

        AnalyticsService analyticsService = new AnalyticsService();
        workspace1 = new Workspace(USER_1, new TestNoteDAO(), new TestTaskDAO(), new TestPeriodDAO(), new TestMoodDAO(), analyticsService);
        workspace2 = new Workspace(USER_2, new TestNoteDAO(), new TestTaskDAO(), new TestPeriodDAO(), new TestMoodDAO(), analyticsService);
    }

    @Test
    @DisplayName("Constructor enforces positive user_id and valid dependencies")
    void testConstructorValidation() {
        assertThrows(IllegalArgumentException.class, () -> new Workspace(0));
        assertThrows(IllegalArgumentException.class, () -> new Workspace(-5));
        assertThrows(NullPointerException.class, () -> new Workspace(USER_1, null, new TestTaskDAO(), new TestPeriodDAO(), new TestMoodDAO(), new AnalyticsService()));
    }

    @Test
    @DisplayName("addNote adds note assigned to user and retrieves analytics")
    void testAddNoteAndAnalytics() throws SQLException {
        Note n = workspace1.addNote("Architecture", "Clean modular code", "Study");
        assertNotNull(n);
        assertTrue(n.getId() > 0);
        assertEquals(USER_1, n.getUserId());

        AnalyticsData data = workspace1.getAnalytics();
        DashboardCounts counts = data.getCounts();
        assertEquals(1, counts.getNotes());
        assertEquals(0, counts.getTasks());
    }

    @Test
    @DisplayName("addTask adds task with correct fields and updates task statistics")
    void testAddTaskAndAnalytics() throws SQLException {
        Task t = workspace1.addTask("Implement tests", LocalDate.of(2026, 11, 15), Priority.HIGH);
        assertNotNull(t);
        assertTrue(t.getId() > 0);
        assertEquals(USER_1, t.getUserId());
        assertEquals(Status.TODO, t.getStatus());

        AnalyticsData data = workspace1.getAnalytics();
        TaskStats stats = data.getTaskStats();
        assertEquals(1, stats.getTotal());
        assertEquals(1, stats.getPending());
        assertEquals(0, stats.getCompleted());
        assertEquals(0, stats.getCompletionPercentage());
    }

    @Test
    @DisplayName("Two-Account Privacy: User 2's workspace cannot see User 1's records")
    void testUserIsolationAndPrivacy() throws SQLException {
        // User 1 populates records
        workspace1.addNote("Private Note", "User 1 secret", "Personal");
        workspace1.addTask("User 1 Task", LocalDate.of(2026, 11, 20), Priority.HIGH);
        workspace1.addCycle(LocalDate.of(2026, 9, 15), 28);
        workspace1.addMood(LocalDate.of(2026, 10, 10), Mood.HAPPY, "Feeling great", 0, 8.0, 7);

        // Verify User 1 sees all items
        AnalyticsData data1 = workspace1.getAnalytics();
        assertEquals(1, data1.getCounts().getNotes());
        assertEquals(1, data1.getCounts().getTasks());
        assertEquals(1, data1.getCounts().getMoods());
        assertEquals(1, data1.getCounts().getCycles());
        assertFalse(data1.getRecentActivity().isEmpty());

        // Verify User 2 sees an empty workspace
        AnalyticsData data2 = workspace2.getAnalytics();
        assertEquals(0, data2.getCounts().getNotes(), "User 2 must not see User 1's notes");
        assertEquals(0, data2.getCounts().getTasks(), "User 2 must not see User 1's tasks");
        assertEquals(0, data2.getCounts().getMoods(), "User 2 must not see User 1's moods");
        assertEquals(0, data2.getCounts().getCycles(), "User 2 must not see User 1's cycles");
        assertTrue(data2.getRecentActivity().isEmpty(), "User 2 recent activity must be empty");

        // User 2 adds a note and verifies it is only in User 2's workspace
        workspace2.addNote("User 2 Note", "User 2 content", "General");
        assertEquals(1, workspace2.getAnalytics().getCounts().getNotes());
        assertEquals(1, workspace1.getAnalytics().getCounts().getNotes()); // User 1 count unchanged
    }

    @Test
    @DisplayName("Polymorphic items retrieval calls summary() on all item types")
    void testPolymorphicAllItems() throws SQLException {
        workspace1.addNote("OOP revision", "Abstraction & Polymorphism", "Study");
        workspace1.addTask("Finish diagram", LocalDate.of(2026, 11, 1), Priority.HIGH);
        workspace1.addCycle(LocalDate.of(2026, 9, 20), 28);
        workspace1.addMood(LocalDate.of(2026, 10, 1), Mood.CALM, "none", 1, 7.5, 8);

        List<WorkspaceItem> items = workspace1.getAllItems();
        assertEquals(4, items.size());

        for (WorkspaceItem item : items) {
            String summary = item.summary();
            assertNotNull(summary);
            assertFalse(summary.isBlank());
            assertTrue(summary.startsWith("[Note]") || summary.startsWith("[Task]") ||
                       summary.startsWith("[Cycle]") || summary.startsWith("[Mood]"));
        }
    }
}

