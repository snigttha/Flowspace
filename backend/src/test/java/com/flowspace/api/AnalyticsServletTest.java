package com.flowspace.api;

import com.flowspace.dao.*;
import com.flowspace.model.AnalyticsData;
import com.flowspace.model.Workspace;
import com.flowspace.service.AnalyticsService;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AnalyticsServlet – API Integration and Privacy Tests")
class AnalyticsServletTest {

    private static final String MEM_URL = "jdbc:sqlite:file:servletmem?mode=memory&cache=shared";
    private static Connection sharedConn;

    private static final int USER_A = 201;
    private static final int USER_B = 202;

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

    private AnalyticsServlet servlet;

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
        try (Statement st = sharedConn.createStatement()) {
            st.execute("DELETE FROM notes");
            st.execute("DELETE FROM tasks");
            st.execute("DELETE FROM period_logs");
            st.execute("DELETE FROM mood_entries");
        }

        servlet = new AnalyticsServlet() {
            @Override
            protected Workspace createWorkspace(int userId) {
                return new Workspace(
                    userId,
                    new TestNoteDAO(),
                    new TestTaskDAO(),
                    new TestPeriodDAO(),
                    new TestMoodDAO(),
                    new AnalyticsService()
                );
            }
        };
    }

    @Test
    @DisplayName("GET /api/analytics without session returns 401 Unauthorized")
    void testUnauthenticatedNoSession() throws Exception {
        HttpServletRequest req = createMockRequest(null);
        StringWriter sw = new StringWriter();
        AtomicInteger status = new AtomicInteger(200);
        HttpServletResponse resp = createMockResponse(sw, status);

        servlet.doGet(req, resp);

        assertEquals(401, status.get());
        JsonObject json = JsonParser.parseString(sw.toString()).getAsJsonObject();
        assertTrue(json.has("error"));
        assertEquals("Please log in.", json.get("error").getAsString());
    }

    @Test
    @DisplayName("GET /api/analytics with valid session returns 200 OK and complete JSON structure")
    void testAuthenticatedValidSession() throws Exception {
        // Pre-populate data for USER_A
        Workspace wsA = new Workspace(
            USER_A,
            new TestNoteDAO(),
            new TestTaskDAO(),
            new TestPeriodDAO(),
            new TestMoodDAO(),
            new AnalyticsService()
        );
        wsA.addNote("Sprint planning", "Team goals", "Study");
        wsA.addTask("Review PR", LocalDate.of(2026, 11, 5), com.flowspace.model.Task.Priority.HIGH);
        wsA.addCycle(LocalDate.of(2026, 9, 20), 28);
        wsA.addMood(LocalDate.of(2026, 10, 1), com.flowspace.model.MoodEntry.Mood.CALM, "none", 1, 7.5, 8);

        HttpServletRequest req = createMockRequest(USER_A);
        StringWriter sw = new StringWriter();
        AtomicInteger status = new AtomicInteger(200);
        HttpServletResponse resp = createMockResponse(sw, status);

        servlet.doGet(req, resp);

        assertEquals(200, status.get());
        JsonObject json = JsonParser.parseString(sw.toString()).getAsJsonObject();

        assertEquals(USER_A, json.get("userId").getAsInt());
        assertTrue(json.has("counts"));
        JsonObject counts = json.getAsJsonObject("counts");
        assertEquals(1, counts.get("notes").getAsInt());
        assertEquals(1, counts.get("tasks").getAsInt());
        assertEquals(1, counts.get("moods").getAsInt());
        assertEquals(1, counts.get("cycles").getAsInt());

        assertTrue(json.has("taskStats"));
        assertTrue(json.has("cycleStats"));
        assertTrue(json.has("moodTrends"));
        assertTrue(json.has("recentActivity"));
        assertTrue(json.has("insights"));
    }

    @Test
    @DisplayName("Two-Account API Privacy: User B cannot see User A's data through the endpoint")
    void testTwoAccountApiPrivacy() throws Exception {
        // User A adds sensitive records
        Workspace wsA = new Workspace(
            USER_A,
            new TestNoteDAO(),
            new TestTaskDAO(),
            new TestPeriodDAO(),
            new TestMoodDAO(),
            new AnalyticsService()
        );
        wsA.addNote("Confidential User A Note", "Secret", "Personal");
        wsA.addTask("Secret User A Task", LocalDate.of(2026, 11, 10), com.flowspace.model.Task.Priority.HIGH);

        // User B requests analytics via API
        HttpServletRequest reqB = createMockRequest(USER_B);
        StringWriter swB = new StringWriter();
        AtomicInteger statusB = new AtomicInteger(200);
        HttpServletResponse respB = createMockResponse(swB, statusB);

        servlet.doGet(reqB, respB);

        assertEquals(200, statusB.get());
        JsonObject jsonB = JsonParser.parseString(swB.toString()).getAsJsonObject();

        assertEquals(USER_B, jsonB.get("userId").getAsInt());
        JsonObject countsB = jsonB.getAsJsonObject("counts");
        assertEquals(0, countsB.get("notes").getAsInt(), "User B must have 0 notes");
        assertEquals(0, countsB.get("tasks").getAsInt(), "User B must have 0 tasks");
        assertEquals(0, jsonB.getAsJsonArray("recentActivity").size(), "User B must have 0 recent activity");
    }

    // ── Helper Reflection Proxies ────────────────────────────────────────

    private HttpServletRequest createMockRequest(Integer userId) {
        HttpSession session = (userId != null) ? (HttpSession) Proxy.newProxyInstance(
            HttpSession.class.getClassLoader(),
            new Class<?>[]{HttpSession.class},
            (proxy, method, args) -> {
                if ("getAttribute".equals(method.getName()) && "userId".equals(args[0])) {
                    return userId;
                }
                return null;
            }
        ) : null;

        return (HttpServletRequest) Proxy.newProxyInstance(
            HttpServletRequest.class.getClassLoader(),
            new Class<?>[]{HttpServletRequest.class},
            (proxy, method, args) -> {
                if ("getSession".equals(method.getName())) {
                    return session;
                }
                return null;
            }
        );
    }

    private HttpServletResponse createMockResponse(StringWriter sw, AtomicInteger status) {
        PrintWriter pw = new PrintWriter(sw);
        return (HttpServletResponse) Proxy.newProxyInstance(
            HttpServletResponse.class.getClassLoader(),
            new Class<?>[]{HttpServletResponse.class},
            (proxy, method, args) -> {
                if ("setStatus".equals(method.getName())) {
                    status.set((int) args[0]);
                    return null;
                }
                if ("getStatus".equals(method.getName())) {
                    return status.get();
                }
                if ("getWriter".equals(method.getName())) {
                    return pw;
                }
                if ("setContentType".equals(method.getName())) {
                    return null;
                }
                return null;
            }
        );
    }
}

