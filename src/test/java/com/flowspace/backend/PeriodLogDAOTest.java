package com.flowspace.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class PeriodLogDAOTest {

    private PeriodLogDAO dao;

    @BeforeEach
    void setUp() throws Exception {
        String url = "jdbc:h2:mem:period_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, "sa", "");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE period_logs (log_id INTEGER AUTO_INCREMENT PRIMARY KEY, "
                + "user_id INTEGER NOT NULL, cycle_start_date DATE NOT NULL, cycle_length INTEGER NOT NULL, "
                + "predicted_next_date DATE, UNIQUE (user_id, cycle_start_date))");
        }
        dao = new PeriodLogDAO(dataSource);
    }

    @Test
    void sameStartDateUpdatesTheExistingRow() throws Exception {
        LocalDate start = LocalDate.parse("2026-09-01");
        PeriodLog first = dao.save(new PeriodLog(10, start, 28));
        PeriodLog updated = dao.save(new PeriodLog(10, start, 32));

        assertEquals(first.getId(), updated.getId());
        assertEquals(32, updated.getCycleLength());
        assertEquals(1, dao.findAll(10).size());
    }

    @Test
    void listIsOrderedAndScopedToTheOwner() throws Exception {
        dao.save(new PeriodLog(10, LocalDate.parse("2026-09-20"), 28));
        dao.save(new PeriodLog(10, LocalDate.parse("2026-08-20"), 27));
        dao.save(new PeriodLog(20, LocalDate.parse("2026-08-01"), 29));

        var ownerLogs = dao.findAll(10);
        assertEquals(2, ownerLogs.size());
        assertEquals(LocalDate.parse("2026-08-20"), ownerLogs.get(0).getCycleStartDate());
        assertTrue(ownerLogs.stream().allMatch(log -> log.getUserId() == 10));
        assertFalse(dao.findAll(30).iterator().hasNext());
    }
}