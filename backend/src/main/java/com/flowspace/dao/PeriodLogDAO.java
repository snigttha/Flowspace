package com.flowspace.dao;

import com.flowspace.db.DBConnection;
import com.flowspace.model.PeriodLog;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

