package com.flowspace.dao;

import com.flowspace.model.Note;
import com.flowspace.model.Note.Category;
import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link NoteDAO} against an in-memory SQLite database.
 *
 * <p>Uses a subclass of NoteDAO to override connection acquisition so tests
 * do not touch the real database file and run completely self-contained.
 *
 * <p>PLAN.md Member 2 required tests:
 * <ul>
 *   <li>NoteDAO create, update, delete, search</li>
 *   <li>Another user cannot see the note (privacy check)</li>
 * </ul>
 *
 * @author Member 2 – Notes module
 */
@DisplayName("NoteDAO – integration tests (in-memory SQLite)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NoteDAOTest {

    /**
     * Shared in-memory SQLite database URL (named so that all connections
     * share the same in-memory instance during the test run).
     */
    private static final String MEM_URL =
        "jdbc:sqlite:file:testmem?mode=memory&cache=shared";

    private static Connection sharedConn; // kept open to keep the DB alive
    private NoteDAO dao;

    private static final int USER_A = 1;
    private static final int USER_B = 2;

    // ── Test-only NoteDAO subclass ────────────────────────────────────────

    /**
     * Subclass that uses the in-memory connection URL instead of the
     * production DBConnection helper.
     */
    static class TestNoteDAO extends NoteDAO {
        @Override
        protected Connection openConnection() throws SQLException {
            return DriverManager.getConnection(MEM_URL);
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    @BeforeAll
    static void createSchema() throws Exception {
        sharedConn = DriverManager.getConnection(MEM_URL);
        try (Statement st = sharedConn.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS notes (
                  note_id    INTEGER PRIMARY KEY AUTOINCREMENT,
                  user_id    INTEGER NOT NULL,
                  title      TEXT    NOT NULL,
                  content    TEXT    NOT NULL,
                  category   TEXT    NOT NULL DEFAULT 'General',
                  created_at TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP
                )""");
        }
    }

    @AfterAll
    static void dropSchema() throws Exception {
        if (sharedConn != null && !sharedConn.isClosed())
            sharedConn.close();
    }

    @BeforeEach
    void setup() {
        dao = new TestNoteDAO();
    }

    @AfterEach
    void clearNotes() throws Exception {
        try (Statement st = sharedConn.createStatement()) {
            st.execute("DELETE FROM notes");
            // Reset auto-increment so IDs are predictable across tests
            st.execute("DELETE FROM sqlite_sequence WHERE name='notes'");
        }
    }

    // ── create ─────────────────────────────────────────────────────────────

    @Test @Order(1)
    @DisplayName("create() persists a note and returns it with a generated id")
    void createPersistsNote() throws SQLException {
        Note n = new Note(USER_A, "Shopping list", "Milk, eggs, bread", Category.Personal);
        Note saved = dao.create(n);

        assertTrue(saved.getId() > 0, "id should be > 0");
        assertEquals("Shopping list",     saved.getTitle());
        assertEquals("Milk, eggs, bread", saved.getContent());
        assertEquals(Category.Personal,   saved.getCategory());
        assertEquals(USER_A,              saved.getUserId());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
    }

    @Test @Order(2)
    @DisplayName("findById() returns the note for its owner")
    void findByIdReturnsOwnerNote() throws SQLException {
        Note saved = dao.create(new Note(USER_A, "T", "C", Category.General));
        Optional<Note> found = dao.findById(saved.getId(), USER_A);
        assertTrue(found.isPresent());
        assertEquals(saved.getId(), found.get().getId());
    }

    // ── Privacy checks ────────────────────────────────────────────────────

    @Test @Order(3)
    @DisplayName("findById() returns empty for a different user – privacy check")
    void findByIdPrivacyCheck() throws SQLException {
        Note saved = dao.create(new Note(USER_A, "Private", "Secret", Category.Personal));
        Optional<Note> forB = dao.findById(saved.getId(), USER_B);
        assertTrue(forB.isEmpty(), "USER_B must NOT see USER_A's note");
    }

    @Test @Order(4)
    @DisplayName("findAll() scopes results to the requesting user")
    void findAllPrivacyCheck() throws SQLException {
        dao.create(new Note(USER_A, "A note", "Content A", Category.Study));
        dao.create(new Note(USER_B, "B note", "Content B", Category.Health));

        List<Note> forA = dao.findAll(USER_A);
        assertEquals(1, forA.size());
        assertEquals("A note", forA.get(0).getTitle());

        List<Note> forB = dao.findAll(USER_B);
        assertEquals(1, forB.size());
        assertEquals("B note", forB.get(0).getTitle());
    }

    // ── update ────────────────────────────────────────────────────────────

    @Test @Order(5)
    @DisplayName("update() changes fields and is reflected in findById()")
    void updateChangesFields() throws SQLException {
        Note saved = dao.create(new Note(USER_A, "Original", "Body", Category.General));
        saved.edit("Updated Title", "Updated body.", "Study");
        Optional<Note> updated = dao.update(saved);

        assertTrue(updated.isPresent());
        assertEquals("Updated Title", updated.get().getTitle());
        assertEquals("Updated body.", updated.get().getContent());
        assertEquals(Category.Study,  updated.get().getCategory());
    }

    @Test @Order(6)
    @DisplayName("update() returns empty when userId does not own the note")
    void updateWrongUserReturnsEmpty() throws SQLException {
        Note saved = dao.create(new Note(USER_A, "Mine", "Body", Category.General));
        saved.setUserId(USER_B); // pretend different user
        saved.edit("Hacked", "Hacked body.", "Ideas");
        Optional<Note> result = dao.update(saved);
        assertTrue(result.isEmpty(), "Wrong user must not be able to update");
    }

    // ── delete ────────────────────────────────────────────────────────────

    @Test @Order(7)
    @DisplayName("delete() removes the note for the owner")
    void deleteRemovesNote() throws SQLException {
        Note saved = dao.create(new Note(USER_A, "Gone", "Bye", Category.Ideas));
        boolean removed = dao.delete(saved.getId(), USER_A);
        assertTrue(removed);
        assertTrue(dao.findById(saved.getId(), USER_A).isEmpty());
    }

    @Test @Order(8)
    @DisplayName("delete() returns false when user doesn't own the note")
    void deleteWrongUserReturnsFalse() throws SQLException {
        Note saved = dao.create(new Note(USER_A, "Still here", "Content", Category.General));
        boolean removed = dao.delete(saved.getId(), USER_B);
        assertFalse(removed, "USER_B must not delete USER_A's note");
        assertTrue(dao.findById(saved.getId(), USER_A).isPresent());
    }

    // ── search ────────────────────────────────────────────────────────────

    @Test @Order(9)
    @DisplayName("search() finds notes by keyword in title")
    void searchByTitleKeyword() throws SQLException {
        dao.create(new Note(USER_A, "Java OOP notes",     "Inheritance",   Category.Study));
        dao.create(new Note(USER_A, "Shopping list",      "Milk, eggs",    Category.Personal));
        dao.create(new Note(USER_A, "Java streams guide", "map, filter",   Category.Study));

        List<Note> results = dao.search(USER_A, "java");
        assertEquals(2, results.size());
    }

    @Test @Order(10)
    @DisplayName("search() finds notes by keyword in content")
    void searchByContentKeyword() throws SQLException {
        dao.create(new Note(USER_A, "Study plan", "Learn encapsulation", Category.Study));
        dao.create(new Note(USER_A, "Diary",      "Had a great day",     Category.Personal));

        List<Note> results = dao.search(USER_A, "encapsulation");
        assertEquals(1, results.size());
        assertEquals("Study plan", results.get(0).getTitle());
    }

    @Test @Order(11)
    @DisplayName("search() is case-insensitive")
    void searchCaseInsensitive() throws SQLException {
        dao.create(new Note(USER_A, "UPPER CASE NOTE", "Content", Category.General));
        assertEquals(1, dao.search(USER_A, "upper case").size());
    }

    @Test @Order(12)
    @DisplayName("search() returns empty list for non-matching keyword")
    void searchNoResults() throws SQLException {
        dao.create(new Note(USER_A, "Title", "Body", Category.General));
        assertTrue(dao.search(USER_A, "xyzzy_nonexistent").isEmpty());
    }

    @Test @Order(13)
    @DisplayName("search() does not leak results to another user")
    void searchPrivacyCheck() throws SQLException {
        dao.create(new Note(USER_A, "Secret OOP note", "Private", Category.Study));
        assertTrue(dao.search(USER_B, "OOP").isEmpty(),
            "USER_B must not see USER_A's notes via search");
    }

    // ── findByCategory ────────────────────────────────────────────────────

    @Test @Order(14)
    @DisplayName("findByCategory() returns only notes in the given category")
    void findByCategoryFilters() throws SQLException {
        dao.create(new Note(USER_A, "Study 1",  "Content", Category.Study));
        dao.create(new Note(USER_A, "Study 2",  "Content", Category.Study));
        dao.create(new Note(USER_A, "Personal", "Content", Category.Personal));

        assertEquals(2, dao.findByCategory(USER_A, Category.Study).size());
        assertEquals(1, dao.findByCategory(USER_A, Category.Personal).size());
        assertEquals(0, dao.findByCategory(USER_A, Category.Ideas).size());
    }

    // ── searchByCategory ──────────────────────────────────────────────────

    @Test @Order(15)
    @DisplayName("searchByCategory() combines keyword and category filters")
    void searchByCategoryTest() throws SQLException {
        dao.create(new Note(USER_A, "Java Study",   "OOP",     Category.Study));
        dao.create(new Note(USER_A, "Java Ideas",   "Frameworks", Category.Ideas));
        dao.create(new Note(USER_A, "Python Study", "Pandas",  Category.Study));

        List<Note> results = dao.searchByCategory(USER_A, "java", Category.Study);
        assertEquals(1, results.size());
        assertEquals("Java Study", results.get(0).getTitle());
    }
}
