package com.flowspace.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flowspace.model.PeriodLog;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PeriodLogDAOTest {

    private static final String URL = "jdbc:sqlite:file:periodtest?mode=memory&cache=shared";
    private static Connection keepAlive;
    private PeriodLogDAO dao;

    private static class TestPeriodLogDAO extends PeriodLogDAO {
        @Override
        protected Connection openConnection() throws SQLException {
            return DriverManager.getConnection(URL);
        }
    }

    @BeforeAll
    static void createSchema() throws Exception {
        keepAlive = DriverManager.getConnection(URL);
        try (Statement statement = keepAlive.createStatement()) {
            statement.execute("CREATE TABLE period_logs (log_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "user_id INTEGER NOT NULL, cycle_start_date TEXT NOT NULL, cycle_length INTEGER NOT NULL, "
                + "predicted_next_date TEXT, UNIQUE(user_id, cycle_start_date))");
        }
    }

    @AfterAll
    static void closeDatabase() throws Exception {
        keepAlive.close();
    }

    @BeforeEach
    void setUp() throws Exception {
        dao = new TestPeriodLogDAO();
        try (Statement statement = keepAlive.createStatement()) {
            statement.execute("DELETE FROM period_logs");
            statement.execute("DELETE FROM sqlite_sequence WHERE name='period_logs'");
        }
    }

    @Test
    void sameDateUpdatesInsteadOfDuplicating() throws Exception {
        LocalDate date = LocalDate.parse("2026-09-01");
        PeriodLog first = dao.save(new PeriodLog(10, date, 28));
        PeriodLog updated = dao.save(new PeriodLog(10, date, 32));
        assertEquals(first.getId(), updated.getId());
        assertEquals(32, updated.getCycleLength());
        assertEquals(1, dao.findAll(10).size());
    }

    @Test
    void listAndLatestQueriesAreDateOrderedAndPrivate() throws Exception {
        dao.save(new PeriodLog(10, LocalDate.parse("2026-09-20"), 28));
        dao.save(new PeriodLog(10, LocalDate.parse("2026-08-20"), 27));
        dao.save(new PeriodLog(20, LocalDate.parse("2026-08-01"), 29));

        var ownerLogs = dao.findAll(10);
        assertEquals(2, ownerLogs.size());
        assertEquals(LocalDate.parse("2026-08-20"), ownerLogs.get(0).getCycleStartDate());
        assertEquals(LocalDate.parse("2026-09-20"), dao.findLatest(10).orElseThrow().getCycleStartDate());
        assertTrue(ownerLogs.stream().allMatch(log -> log.getUserId() == 10));
        assertTrue(dao.findAll(30).isEmpty());
        assertFalse(dao.findById(ownerLogs.get(0).getId(), 20).isPresent());
    }
}