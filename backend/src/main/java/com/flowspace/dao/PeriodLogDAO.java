package com.flowspace.dao;

import com.flowspace.db.DBConnection;
import com.flowspace.model.PeriodLog;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for the {@code period_logs} table.
 *
 * <p>Implements {@link Dao}{@code <PeriodLog>} with user isolation.
 * Automatically computes / updates predicted next dates where applicable.
 *
 * @author Member 6 (Workspace & Integration Lead)
 */
public class PeriodLogDAO implements Dao<PeriodLog> {

    private static final String SQL_FIND_BY_USER_AND_START =
        "SELECT * FROM period_logs WHERE user_id = ? AND cycle_start_date = ?";

    private static final String SQL_INSERT =
        "INSERT INTO period_logs (user_id, cycle_start_date, cycle_length, predicted_next_date) VALUES (?, ?, ?, ?)";

    private static final String SQL_FIND_BY_ID =
        "SELECT * FROM period_logs WHERE log_id = ? AND user_id = ?";

    private static final String SQL_FIND_ALL =
        "SELECT * FROM period_logs WHERE user_id = ? ORDER BY cycle_start_date ASC";

    private static final String SQL_UPDATE =
        "UPDATE period_logs SET cycle_length = ?, predicted_next_date = ? WHERE log_id = ? AND user_id = ?";

    private static final String SQL_DELETE =
        "DELETE FROM period_logs WHERE log_id = ? AND user_id = ?";

public class PeriodLogDAO implements Dao<PeriodLog> {

    protected Connection openConnection() throws SQLException {
        return DBConnection.get();
    }

    @Override
    public PeriodLog create(PeriodLog item) throws SQLException {
        item.validate();
        // Check if an entry for user and cycle_start_date already exists (upsert behavior)
        Optional<PeriodLog> existing = findByUserAndDate(item.getUserId(), item.getCycleStartDate());
        if (existing.isPresent()) {
            PeriodLog ex = existing.get();
            ex.setCycleLength(item.getCycleLength());
            ex.setPredictedNextDate(item.getPredictedNextDate());
            return update(ex).orElseThrow(() -> new SQLException("Failed to update existing period log"));
        }

        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt   (1, item.getUserId());
            ps.setString(2, item.getCycleStartDate().toString());
            ps.setInt   (3, item.getCycleLength());
            ps.setString(4, item.getPredictedNextDate() != null ? item.getPredictedNextDate().toString() : null);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int generated = keys.getInt(1);
                    return findById(generated, item.getUserId())
                        .orElseThrow(() -> new SQLException("Insert succeeded but period log row not found"));
                }
            }
        }
        throw new SQLException("Insert did not return a generated key");
    }

    public Optional<PeriodLog> findByUserAndDate(int userId, LocalDate startDate) throws SQLException {
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(SQL_FIND_BY_USER_AND_START)) {
            ps.setInt(1, userId);
            ps.setString(2, startDate.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
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
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(SQL_FIND_BY_ID)) {
            ps.setInt(1, id);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
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
        List<PeriodLog> list = new ArrayList<>();
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(SQL_FIND_ALL)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    public Optional<PeriodLog> findLatest(int userId) throws SQLException {
        List<PeriodLog> all = findAll(userId);
        if (all.isEmpty()) return Optional.empty();
        return Optional.of(all.get(all.size() - 1));
    }

    @Override
    public Optional<PeriodLog> update(PeriodLog item) throws SQLException {
        item.validate();
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(SQL_UPDATE)) {
            ps.setInt   (1, item.getCycleLength());
            ps.setString(2, item.getPredictedNextDate() != null ? item.getPredictedNextDate().toString() : null);
            ps.setInt   (3, item.getId());
            ps.setInt   (4, item.getUserId());
            int affected = ps.executeUpdate();
            if (affected == 0) return Optional.empty();
        }
        return findById(item.getId(), item.getUserId());
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
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(SQL_DELETE)) {
            ps.setInt(1, id);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    private PeriodLog map(ResultSet rs) throws SQLException {
        PeriodLog log = new PeriodLog();
        log.setId(rs.getInt("log_id"));
        log.setUserId(rs.getInt("user_id"));
        String start = rs.getString("cycle_start_date");
        if (start != null) {
            log.setCycleStartDate(LocalDate.parse(start.substring(0, 10)));
        }
        log.setCycleLength(rs.getInt("cycle_length"));
        String pred = rs.getString("predicted_next_date");
        if (pred != null && !pred.isBlank()) {
            log.setPredictedNextDate(LocalDate.parse(pred.substring(0, 10)));
        }
        log.setCreatedAt(LocalDateTime.now());
        return log;
    }
}

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
