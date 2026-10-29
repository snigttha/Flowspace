package com.flowspace.dao;

import com.flowspace.db.DBConnection;
import com.flowspace.model.MoodEntry;
import com.flowspace.model.MoodEntry.Mood;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for the {@code mood_entries} table.
 *
 * <p>Implements {@link Dao}{@code <MoodEntry>} with user isolation.
 * Automatically upserts when an entry on the same date already exists.
 *
 * @author Member 6 (Workspace & Integration Lead)
 */
public class MoodEntryDAO implements Dao<MoodEntry> {

    private static final String SQL_FIND_BY_USER_AND_DATE =
        "SELECT * FROM mood_entries WHERE user_id = ? AND entry_date = ?";

    private static final String SQL_INSERT =
        "INSERT INTO mood_entries (user_id, entry_date, mood, symptoms, pain_level, sleep_hours, water_intake) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?)";

    private static final String SQL_FIND_BY_ID =
        "SELECT * FROM mood_entries WHERE entry_id = ? AND user_id = ?";

    private static final String SQL_FIND_ALL =
        "SELECT * FROM mood_entries WHERE user_id = ? ORDER BY entry_date ASC";

    private static final String SQL_FIND_RECENT =
        "SELECT * FROM mood_entries WHERE user_id = ? ORDER BY entry_date DESC LIMIT ?";

    private static final String SQL_UPDATE =
        "UPDATE mood_entries SET mood = ?, symptoms = ?, pain_level = ?, sleep_hours = ?, water_intake = ? " +
        "WHERE entry_id = ? AND user_id = ?";

    private static final String SQL_DELETE =
        "DELETE FROM mood_entries WHERE entry_id = ? AND user_id = ?";

    protected Connection openConnection() throws SQLException {
        return DBConnection.get();
    }

    @Override
    public MoodEntry create(MoodEntry item) throws SQLException {
        item.validate();
        Optional<MoodEntry> existing = findByUserAndDate(item.getUserId(), item.getEntryDate());
        if (existing.isPresent()) {
            MoodEntry ex = existing.get();
            ex.setMood(item.getMood());
            ex.setSymptoms(item.getSymptoms());
            ex.setPainLevel(item.getPainLevel());
            ex.setSleepHours(item.getSleepHours());
            ex.setWaterIntake(item.getWaterIntake());
            return update(ex).orElseThrow(() -> new SQLException("Failed to update existing mood entry"));
        }

        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt   (1, item.getUserId());
            ps.setString(2, item.getEntryDate().toString());
            ps.setString(3, item.getMood().name());
            ps.setString(4, item.getSymptoms());
            ps.setInt   (5, item.getPainLevel());
            ps.setDouble(6, item.getSleepHours());
            ps.setInt   (7, item.getWaterIntake());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int generated = keys.getInt(1);
                    return findById(generated, item.getUserId())
                        .orElseThrow(() -> new SQLException("Insert succeeded but mood entry row not found"));
                }
            }
        }
        throw new SQLException("Insert did not return a generated key");
    }

    public Optional<MoodEntry> findByUserAndDate(int userId, LocalDate entryDate) throws SQLException {
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(SQL_FIND_BY_USER_AND_DATE)) {
            ps.setInt(1, userId);
            ps.setString(2, entryDate.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<MoodEntry> findById(int id, int userId) throws SQLException {
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
    public List<MoodEntry> findAll(int userId) throws SQLException {
        List<MoodEntry> list = new ArrayList<>();
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(SQL_FIND_ALL)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    public List<MoodEntry> findRecent(int userId, int limit) throws SQLException {
        List<MoodEntry> list = new ArrayList<>();
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(SQL_FIND_RECENT)) {
            ps.setInt(1, userId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    @Override
    public Optional<MoodEntry> update(MoodEntry item) throws SQLException {
        item.validate();
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(SQL_UPDATE)) {
            ps.setString(1, item.getMood().name());
            ps.setString(2, item.getSymptoms());
            ps.setInt   (3, item.getPainLevel());
            ps.setDouble(4, item.getSleepHours());
            ps.setInt   (5, item.getWaterIntake());
            ps.setInt   (6, item.getId());
            ps.setInt   (7, item.getUserId());
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

    private MoodEntry map(ResultSet rs) throws SQLException {
        MoodEntry m = new MoodEntry();
        m.setId(rs.getInt("entry_id"));
        m.setUserId(rs.getInt("user_id"));
        String date = rs.getString("entry_date");
        if (date != null) {
            m.setEntryDate(LocalDate.parse(date.substring(0, 10)));
        }
        m.setMood(Mood.fromString(rs.getString("mood")));
        m.setSymptoms(rs.getString("symptoms"));
        m.setPainLevel(rs.getInt("pain_level"));
        m.setSleepHours(rs.getDouble("sleep_hours"));
        m.setWaterIntake(rs.getInt("water_intake"));
        m.setCreatedAt(LocalDateTime.now());
        return m;
    }
}

