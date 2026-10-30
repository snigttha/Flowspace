package com.flowspace.backend;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.stereotype.Repository;

@Repository
public class PeriodLogDAO {

    private final DataSource dataSource;

    public PeriodLogDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<PeriodLog> findAll(int userId) throws SQLException {
        String sql = "SELECT log_id, user_id, cycle_start_date, cycle_length "
            + "FROM period_logs WHERE user_id = ? ORDER BY cycle_start_date ASC";
        List<PeriodLog> logs = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) logs.add(mapRow(rows));
            }
        }
        return logs;
    }

    public PeriodLog save(PeriodLog log) throws SQLException {
        log.validate();
        String sql = "INSERT INTO period_logs (user_id, cycle_start_date, cycle_length, predicted_next_date) "
            + "VALUES (?, ?, ?, NULL) ON DUPLICATE KEY UPDATE cycle_length = VALUES(cycle_length), "
            + "predicted_next_date = NULL";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, log.getUserId());
            statement.setDate(2, Date.valueOf(log.getCycleStartDate()));
            statement.setInt(3, log.getCycleLength());
            statement.executeUpdate();
        }

        return findByStartDate(log.getUserId(), log.getCycleStartDate());
    }

    private PeriodLog findByStartDate(int userId, java.time.LocalDate startDate) throws SQLException {
        String sql = "SELECT log_id, user_id, cycle_start_date, cycle_length FROM period_logs "
            + "WHERE user_id = ? AND cycle_start_date = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            statement.setDate(2, Date.valueOf(startDate));
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? mapRow(row) : null;
            }
        }
    }

    private PeriodLog mapRow(ResultSet row) throws SQLException {
        PeriodLog log = new PeriodLog();
        log.setId(row.getInt("log_id"));
        log.setUserId(row.getInt("user_id"));
        log.setCycleStartDate(row.getDate("cycle_start_date").toLocalDate());
        log.setCycleLength(row.getInt("cycle_length"));
        return log;
    }
}