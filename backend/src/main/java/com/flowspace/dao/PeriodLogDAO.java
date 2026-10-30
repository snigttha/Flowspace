package com.flowspace.dao;

import com.flowspace.db.DBConnection;
import com.flowspace.model.PeriodLog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PeriodLogDAO implements Dao<PeriodLog> {

    protected Connection openConnection() throws SQLException {
        return DBConnection.get();
    }

    @Override
    public PeriodLog create(PeriodLog log) throws SQLException {
        return save(log);
    }

    public PeriodLog save(PeriodLog log) throws SQLException {
        log.validate();
        try (Connection connection = openConnection()) {
            Integer existingId = findId(connection, log.getUserId(), log.getCycleStartDate().toString());
            if (existingId != null) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE period_logs SET cycle_length = ?, predicted_next_date = NULL "
                            + "WHERE log_id = ? AND user_id = ?")) {
                    statement.setInt(1, log.getCycleLength());
                    statement.setInt(2, existingId);
                    statement.setInt(3, log.getUserId());
                    statement.executeUpdate();
                }
                log.setId(existingId);
                return log;
            }

            String sql = "INSERT INTO period_logs (user_id, cycle_start_date, cycle_length, predicted_next_date) "
                + "VALUES (?, ?, ?, NULL)";
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setInt(1, log.getUserId());
                statement.setString(2, log.getCycleStartDate().toString());
                statement.setInt(3, log.getCycleLength());
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) log.setId(keys.getInt(1));
                }
            }
            return log;
        }
    }

    @Override
    public Optional<PeriodLog> findById(int id, int userId) throws SQLException {
        String sql = "SELECT log_id, user_id, cycle_start_date, cycle_length FROM period_logs "
            + "WHERE log_id = ? AND user_id = ?";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.setInt(2, userId);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(mapRow(row)) : Optional.empty();
            }
        }
    }

    @Override
    public List<PeriodLog> findAll(int userId) throws SQLException {
        String sql = "SELECT log_id, user_id, cycle_start_date, cycle_length FROM period_logs "
            + "WHERE user_id = ? ORDER BY cycle_start_date ASC";
        List<PeriodLog> logs = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) logs.add(mapRow(rows));
            }
        }
        return logs;
    }

    public Optional<PeriodLog> findLatest(int userId) throws SQLException {
        String sql = "SELECT log_id, user_id, cycle_start_date, cycle_length FROM period_logs "
            + "WHERE user_id = ? ORDER BY cycle_start_date DESC LIMIT 1";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(mapRow(row)) : Optional.empty();
            }
        }
    }

    @Override
    public Optional<PeriodLog> update(PeriodLog log) throws SQLException {
        log.validate();
        String sql = "UPDATE period_logs SET cycle_start_date = ?, cycle_length = ?, predicted_next_date = NULL "
            + "WHERE log_id = ? AND user_id = ?";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, log.getCycleStartDate().toString());
            statement.setInt(2, log.getCycleLength());
            statement.setInt(3, log.getId());
            statement.setInt(4, log.getUserId());
            return statement.executeUpdate() == 1 ? Optional.of(log) : Optional.empty();
        }
    }

    @Override
    public boolean delete(int id, int userId) throws SQLException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "DELETE FROM period_logs WHERE log_id = ? AND user_id = ?")) {
            statement.setInt(1, id);
            statement.setInt(2, userId);
            return statement.executeUpdate() == 1;
        }
    }

    private Integer findId(Connection connection, int userId, String startDate) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT log_id FROM period_logs WHERE user_id = ? AND cycle_start_date = ?")) {
            statement.setInt(1, userId);
            statement.setString(2, startDate);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? row.getInt("log_id") : null;
            }
        }
    }

    private PeriodLog mapRow(ResultSet row) throws SQLException {
        PeriodLog log = new PeriodLog();
        log.setId(row.getInt("log_id"));
        log.setUserId(row.getInt("user_id"));
        log.setCycleStartDate(java.time.LocalDate.parse(row.getString("cycle_start_date")));
        log.setCycleLength(row.getInt("cycle_length"));
        return log;
    }
}