package com.flowspace.dao;

import com.flowspace.model.Priority;
import com.flowspace.model.Status;
import com.flowspace.model.Task;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link TaskDAO} against an in-memory SQLite database.
 *
 * <p>PLAN.md Member 3 required tests:
 * <ul>
 *   <li>TaskDAO create, update, delete, status queries</li>
 *   <li>Another user cannot see the task (privacy check)</li>
 *   <li>Status counts for Member 6 analytics</li>
 *   <li>Overdue tasks query</li>
 * </ul>
 *
 * @author Member 3 – Tasks module
 */
@DisplayName("TaskDAO – integration tests (in-memory SQLite)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TaskDAOTest {

    private static final String MEM_URL =
        "jdbc:sqlite:file:tasktestmem?mode=memory&cache=shared";

    private static Connection sharedConn;
    private TaskDAO dao;

    private static final int USER_A = 10;
    private static final int USER_B = 20;

    static class TestTaskDAO extends TaskDAO {
        @Override
        protected Connection openConnection() throws SQLException {
            return DriverManager.getConnection(MEM_URL);
        }
    }

    @BeforeAll
    static void initSchema() throws Exception {
        sharedConn = DriverManager.getConnection(MEM_URL);
        try (Statement s = sharedConn.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS tasks (
                  task_id    INTEGER PRIMARY KEY AUTOINCREMENT,
                  user_id    INTEGER NOT NULL,
                  title      TEXT NOT NULL,
                  due_date   TEXT NOT NULL,
                  priority   TEXT NOT NULL DEFAULT 'MEDIUM',
                  status     TEXT NOT NULL DEFAULT 'TODO',
                  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                );
                CREATE INDEX IF NOT EXISTS idx_tasks_user_status ON tasks(user_id, status);
            """);
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (sharedConn != null && !sharedConn.isClosed()) {
            sharedConn.close();
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        dao = new TestTaskDAO();
        try (Statement s = sharedConn.createStatement()) {
            s.execute("DELETE FROM tasks");
        }
    }

    @Test
    @Order(1)
    @DisplayName("create() persists a task and sets generated task_id")
    void testCreate() throws SQLException {
        Task t = new Task(USER_A, "Write Unit Tests", "2026-11-20", Priority.HIGH);
        Task saved = dao.create(t);

        assertTrue(saved.getId() > 0, "task_id must be assigned");
        assertEquals("Write Unit Tests", saved.getTitle());
        assertEquals(Status.TODO, saved.getStatus());
    }

    @Test
    @Order(2)
    @DisplayName("findById() returns the task for its owner")
    void testFindById() throws SQLException {
        Task t = dao.create(new Task(USER_A, "Setup CI", "2026-11-25", Priority.MEDIUM));
        Optional<Task> opt = dao.findById(t.getId(), USER_A);

        assertTrue(opt.isPresent());
        assertEquals("Setup CI", opt.get().getTitle());
    }

    @Test
    @Order(3)
    @DisplayName("Multi-tenant privacy: User B CANNOT read User A's task")
    void testUserIsolation() throws SQLException {
        Task t = dao.create(new Task(USER_A, "Confidential Task", "2026-11-30", Priority.HIGH));

        Optional<Task> leak = dao.findById(t.getId(), USER_B);
        assertTrue(leak.isEmpty(), "User B must not be able to read User A's task");

        boolean deleted = dao.delete(t.getId(), USER_B);
        assertFalse(deleted, "User B must not be able to delete User A's task");

        // User A can still find it
        assertTrue(dao.findById(t.getId(), USER_A).isPresent());
    }

    @Test
    @Order(4)
    @DisplayName("findByUserAndStatus() filters by status and sorts")
    void testFindByStatus() throws SQLException {
        dao.create(new Task(USER_A, "Task 1", "2026-11-01", Priority.LOW, Status.TODO));
        dao.create(new Task(USER_A, "Task 2", "2026-11-02", Priority.HIGH, Status.IN_PROGRESS));
        dao.create(new Task(USER_A, "Task 3", "2026-11-03", Priority.MEDIUM, Status.DONE));

        List<Task> inProgress = dao.findByUserAndStatus(USER_A, Status.IN_PROGRESS, "due_date");
        assertEquals(1, inProgress.size());
        assertEquals("Task 2", inProgress.get(0).getTitle());

        List<Task> all = dao.findAll(USER_A);
        assertEquals(3, all.size());
    }

    @Test
    @Order(5)
    @DisplayName("update() updates fields")
    void testUpdate() throws SQLException {
        Task t = dao.create(new Task(USER_A, "Initial title", "2026-11-01", Priority.LOW));
        t.setTitle("Updated title");
        t.setPriority(Priority.HIGH);
        t.setDueDate("2026-11-05");

        Optional<Task> updated = dao.update(t);
        assertTrue(updated.isPresent());

        Task reloaded = dao.findById(t.getId(), USER_A).orElseThrow();
        assertEquals("Updated title", reloaded.getTitle());
        assertEquals(Priority.HIGH, reloaded.getPriority());
        assertEquals("2026-11-05", reloaded.getDueDate());
    }

    @Test
    @Order(6)
    @DisplayName("updateStatus() validates and updates status")
    void testUpdateStatus() throws SQLException {
        Task t = dao.create(new Task(USER_A, "Workflow Task", "2026-11-10", Priority.MEDIUM));

        Optional<Task> moved = dao.updateStatus(t.getId(), USER_A, Status.IN_PROGRESS);
        assertTrue(moved.isPresent());
        assertEquals(Status.IN_PROGRESS, moved.get().getStatus());

        Optional<Task> completed = dao.updateStatus(t.getId(), USER_A, Status.DONE);
        assertTrue(completed.isPresent());
        assertEquals(Status.DONE, completed.get().getStatus());

        // Illegal transition DONE -> IN_PROGRESS throws IllegalStateException
        assertThrows(IllegalStateException.class, () ->
            dao.updateStatus(t.getId(), USER_A, Status.IN_PROGRESS));
    }

    @Test
    @Order(7)
    @DisplayName("delete() deletes the task")
    void testDelete() throws SQLException {
        Task t = dao.create(new Task(USER_A, "To be deleted", "2026-11-10", Priority.LOW));
        assertTrue(dao.delete(t.getId(), USER_A));
        assertTrue(dao.findById(t.getId(), USER_A).isEmpty());
    }

    @Test
    @Order(8)
    @DisplayName("Member 6 Dependency: countByStatus() returns accurate counts")
    void testStatusCountsForMember6() throws SQLException {
        dao.create(new Task(USER_A, "Task 1", "2026-11-01", Priority.LOW, Status.TODO));
        dao.create(new Task(USER_A, "Task 2", "2026-11-02", Priority.LOW, Status.TODO));
        dao.create(new Task(USER_A, "Task 3", "2026-11-03", Priority.LOW, Status.IN_PROGRESS));
        dao.create(new Task(USER_A, "Task 4", "2026-11-04", Priority.LOW, Status.DONE));

        Map<Status, Integer> counts = dao.countByStatus(USER_A);
        assertEquals(2, counts.get(Status.TODO));
        assertEquals(1, counts.get(Status.IN_PROGRESS));
        assertEquals(1, counts.get(Status.DONE));
    }

    @Test
    @Order(9)
    @DisplayName("getOverdueTasks() returns past due incomplete tasks")
    void testGetOverdueTasks() throws SQLException {
        String past = LocalDate.now().minusDays(3).toString();
        String future = LocalDate.now().plusDays(3).toString();

        dao.create(new Task(USER_A, "Overdue Task", past, Priority.HIGH, Status.TODO));
        dao.create(new Task(USER_A, "Completed Past Task", past, Priority.LOW, Status.DONE));
        dao.create(new Task(USER_A, "Future Task", future, Priority.MEDIUM, Status.TODO));

        List<Task> overdue = dao.getOverdueTasks(USER_A);
        assertEquals(1, overdue.size());
        assertEquals("Overdue Task", overdue.get(0).getTitle());
    }
}
