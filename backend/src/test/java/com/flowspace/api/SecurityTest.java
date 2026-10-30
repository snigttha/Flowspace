package com.flowspace.api;

import com.flowspace.dao.*;
import com.flowspace.model.Note;
import com.flowspace.model.Note.Category;
import com.flowspace.model.Task;
import com.flowspace.model.Task.Priority;
import com.flowspace.service.NoteManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.*;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FlowSpace Comprehensive Security Test Suite")
class SecurityTest {

    private static final String MEM_URL = "jdbc:sqlite:file:securitymem?mode=memory&cache=shared";
    private static Connection sharedConn;

    private static final int USER_ALICE = 101;
    private static final int USER_BOB   = 102;

    static class MemNoteDAO extends NoteDAO {
        @Override protected Connection openConnection() throws SQLException {
            return DriverManager.getConnection(MEM_URL);
        }
    }

    static class MemTaskDAO extends TaskDAO {
        @Override protected Connection openConnection() throws SQLException {
            return DriverManager.getConnection(MEM_URL);
        }
    }

    static class MemUserDAO extends UserDAO {
        @Override protected Connection openConnection() throws SQLException {
            return DriverManager.getConnection(MEM_URL);
        }
    }

    @BeforeAll
    static void initSchema() throws Exception {
        sharedConn = DriverManager.getConnection(MEM_URL);
        try (Statement st = sharedConn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON;");
            st.execute("""
                CREATE TABLE IF NOT EXISTS users (
                  user_id       INTEGER PRIMARY KEY AUTOINCREMENT,
                  name          TEXT NOT NULL,
                  email         TEXT NOT NULL UNIQUE,
                  password_hash TEXT NOT NULL,
                  created_at    TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                );""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS notes (
                  note_id    INTEGER PRIMARY KEY AUTOINCREMENT,
                  user_id    INTEGER NOT NULL,
                  title      TEXT NOT NULL,
                  content    TEXT NOT NULL,
                  category   TEXT NOT NULL DEFAULT 'General',
                  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                );""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS tasks (
                  task_id    INTEGER PRIMARY KEY AUTOINCREMENT,
                  user_id    INTEGER NOT NULL,
                  title      TEXT NOT NULL,
                  due_date   TEXT NOT NULL,
                  priority   TEXT NOT NULL DEFAULT 'MEDIUM',
                  status     TEXT NOT NULL DEFAULT 'TODO',
                  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                );""");
        }
    }

    @BeforeEach
    void cleanTables() throws Exception {
        try (Statement st = sharedConn.createStatement()) {
            st.execute("DELETE FROM users");
            st.execute("DELETE FROM notes");
            st.execute("DELETE FROM tasks");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. HTTP Security Headers & CSRF Filter Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("SecurityFilter injects X-Content-Type-Options, X-Frame-Options, CSP, and Referrer-Policy")
    void testSecurityHeadersInjected() throws Exception {
        SecurityFilter filter = new SecurityFilter();
        Map<String, String> headers = new HashMap<>();

        HttpServletRequest req = createMockRequest("GET", "/index.html", null, Map.of());
        HttpServletResponse resp = createMockResponse(new StringWriter(), new AtomicInteger(200), headers);

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (r, s) -> chainCalled.set(true);

        filter.doFilter(req, resp, chain);

        assertTrue(chainCalled.get());
        assertEquals("nosniff", headers.get("X-Content-Type-Options"));
        assertEquals("SAMEORIGIN", headers.get("X-Frame-Options"));
        assertEquals("strict-origin-when-cross-origin", headers.get("Referrer-Policy"));
        assertNotNull(headers.get("Content-Security-Policy"));
    }

    @Test
    @DisplayName("SecurityFilter blocks cross-origin state-changing POST requests (CSRF protection)")
    void testCsrfCrossSiteOriginRejected() throws Exception {
        SecurityFilter filter = new SecurityFilter();
        Map<String, String> headers = new HashMap<>();
        StringWriter sw = new StringWriter();
        AtomicInteger status = new AtomicInteger(200);

        // Origin header is an untrusted third-party site
        Map<String, String> reqHeaders = Map.of("Origin", "http://malicious-attacker.com");
        HttpServletRequest req = createMockRequest("POST", "/api/notes", null, reqHeaders);
        HttpServletResponse resp = createMockResponse(sw, status, headers);

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (r, s) -> chainCalled.set(true);

        filter.doFilter(req, resp, chain);

        assertFalse(chainCalled.get(), "Cross-site request should be intercepted before calling chain");
        assertEquals(403, status.get());
        assertTrue(sw.toString().contains("Forbidden: Cross-site request rejected"));
    }

    @Test
    @DisplayName("SecurityFilter allows same-origin state-changing POST requests")
    void testCsrfSameOriginAllowed() throws Exception {
        SecurityFilter filter = new SecurityFilter();
        Map<String, String> headers = new HashMap<>();
        StringWriter sw = new StringWriter();
        AtomicInteger status = new AtomicInteger(200);

        Map<String, String> reqHeaders = Map.of(
            "Origin", "http://localhost:8080",
            "Content-Type", "application/json"
        );
        HttpServletRequest req = createMockRequest("POST", "/api/notes", null, reqHeaders);
        HttpServletResponse resp = createMockResponse(sw, status, headers);

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (r, s) -> chainCalled.set(true);

        filter.doFilter(req, resp, chain);

        assertTrue(chainCalled.get(), "Same-origin request should proceed through filter chain");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Brute Force Mitigation / RateLimiter Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RateLimiter enforces limit after threshold and recovers on reset")
    void testRateLimiterThresholdAndReset() {
        RateLimiter limiter = new RateLimiter(3, 5000);
        String ip = "192.168.1.50";

        assertFalse(limiter.isBlocked(ip));
        limiter.recordFailure(ip); // 1
        assertFalse(limiter.isBlocked(ip));
        limiter.recordFailure(ip); // 2
        assertFalse(limiter.isBlocked(ip));
        limiter.recordFailure(ip); // 3

        assertTrue(limiter.isBlocked(ip), "Should be blocked after 3 failures");

        limiter.reset(ip);
        assertFalse(limiter.isBlocked(ip), "Should be unblocked after reset");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. User Isolation & IDOR Protection Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("IDOR Prevention: Bob cannot edit or delete Alice's note")
    void testIdorNoteProtection() throws Exception {
        MemNoteDAO noteDAO = new MemNoteDAO();
        NoteManager manager = new NoteManager(noteDAO);

        // Alice creates a note
        Note aliceNote = manager.createNote(USER_ALICE, "Alice Secret", "Confidential text", "Personal");
        assertNotNull(aliceNote);

        // Bob attempts to update Alice's note
        assertThrows(Exception.class, () -> {
            manager.updateNote(aliceNote.getId(), USER_BOB, "Tampered Title", "Hacked text", "Personal");
        }, "Updating another user's note must fail (not found / security violation)");

        // Bob attempts to delete Alice's note
        assertThrows(IllegalArgumentException.class, () -> {
            manager.deleteNote(aliceNote.getId(), USER_BOB);
        }, "Deleting another user's note must throw IllegalArgumentException and not delete the record");

        // Verify Alice's note remains untouched
        var fetched = noteDAO.findById(aliceNote.getId(), USER_ALICE);
        assertTrue(fetched.isPresent());
        assertEquals("Alice Secret", fetched.get().getTitle());
    }

    @Test
    @DisplayName("IDOR Prevention: Bob cannot query or delete Alice's task")
    void testIdorTaskProtection() throws Exception {
        MemTaskDAO taskDAO = new MemTaskDAO();

        Task aliceTask = taskDAO.create(new Task(USER_ALICE, "Alice Task", LocalDate.of(2026, 11, 1), Priority.HIGH));
        assertNotNull(aliceTask);

        // Bob cannot find Alice's task by ID
        assertTrue(taskDAO.findById(aliceTask.getId(), USER_BOB).isEmpty());

        // Bob cannot delete Alice's task
        boolean deleted = taskDAO.delete(aliceTask.getId(), USER_BOB);
        assertFalse(deleted, "Bob must not be able to delete Alice's task");

        // Alice's task still exists
        assertTrue(taskDAO.findById(aliceTask.getId(), USER_ALICE).isPresent());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. SQL Injection Resilience
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("SQL Injection Payload: Parameterized queries neutralize injection attempts")
    void testSqlInjectionNeutralization() throws Exception {
        MemNoteDAO noteDAO = new MemNoteDAO();
        NoteManager manager = new NoteManager(noteDAO);

        String maliciousTitle = "Normal Title'; DROP TABLE notes; --";
        String maliciousContent = "' OR '1'='1";

        Note note = manager.createNote(USER_ALICE, maliciousTitle, maliciousContent, "Study");
        assertNotNull(note);

        // Verify that notes table still exists and data was stored safely as literal strings
        var searchResult = manager.getNotes(USER_ALICE, "DROP TABLE", null);
        assertEquals(1, searchResult.size());
        assertEquals(maliciousTitle, searchResult.get(0).getTitle());
        assertEquals(maliciousContent, searchResult.get(0).getContent());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Password Security & BCrypt Verification
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Passwords must be BCrypt hashed and never stored or returned in plaintext")
    void testPasswordBCryptSecurity() throws Exception {
        MemUserDAO userDAO = new MemUserDAO();

        var user = userDAO.create("Charlie", "charlie@flowspace.local", "SuperSecurePassword123!");
        assertNotNull(user);
        assertNotEquals("SuperSecurePassword123!", user.getPasswordHash());
        assertTrue(user.getPasswordHash().startsWith("$2a$"), "Password hash must be valid BCrypt format");

        // Authenticate with valid and invalid passwords
        assertNotNull(userDAO.authenticate("charlie@flowspace.local", "SuperSecurePassword123!"));
        assertNull(userDAO.authenticate("charlie@flowspace.local", "WrongPassword456!"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mock Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private HttpServletRequest createMockRequest(String method, String uri, Integer userId, Map<String, String> headers) {
        HttpSession session = (userId != null) ? (HttpSession) Proxy.newProxyInstance(
            HttpSession.class.getClassLoader(),
            new Class<?>[]{HttpSession.class},
            (proxy, m, args) -> {
                if ("getAttribute".equals(m.getName()) && "userId".equals(args[0])) return userId;
                return null;
            }
        ) : null;

        return (HttpServletRequest) Proxy.newProxyInstance(
            HttpServletRequest.class.getClassLoader(),
            new Class<?>[]{HttpServletRequest.class},
            (proxy, m, args) -> {
                if ("getMethod".equals(m.getName())) return method;
                if ("getRequestURI".equals(m.getName())) return uri;
                if ("getServletPath".equals(m.getName())) return uri;
                if ("getServerName".equals(m.getName())) return "localhost";
                if ("getServerPort".equals(m.getName())) return 8080;
                if ("getRemoteAddr".equals(m.getName())) return "127.0.0.1";
                if ("getSession".equals(m.getName())) return session;
                if ("getHeader".equals(m.getName())) return headers.get((String) args[0]);
                if ("getContentType".equals(m.getName())) return headers.get("Content-Type");
                if ("getReader".equals(m.getName())) return new BufferedReader(new StringReader("{}"));
                return null;
            }
        );
    }

    private HttpServletResponse createMockResponse(StringWriter sw, AtomicInteger status, Map<String, String> headers) {
        PrintWriter pw = new PrintWriter(sw);
        return (HttpServletResponse) Proxy.newProxyInstance(
            HttpServletResponse.class.getClassLoader(),
            new Class<?>[]{HttpServletResponse.class},
            (proxy, m, args) -> {
                if ("setStatus".equals(m.getName())) {
                    status.set((int) args[0]);
                    return null;
                }
                if ("getStatus".equals(m.getName())) return status.get();
                if ("setHeader".equals(m.getName())) {
                    headers.put((String) args[0], (String) args[1]);
                    return null;
                }
                if ("getWriter".equals(m.getName())) return pw;
                if ("setContentType".equals(m.getName())) return null;
                return null;
            }
        );
    }
}
