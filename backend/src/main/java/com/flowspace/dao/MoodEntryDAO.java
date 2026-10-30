package com.flowspace.dao;

import com.flowspace.model.Mood;
import com.flowspace.model.MoodAverages;
import com.flowspace.model.MoodEntry;
import com.flowspace.util.DBConnection;
import com.flowspace.util.ValidationException;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Mood & Wellness entries.
 * Member 5 vertical slice owner.
 * 
 * Enforces:
 * - PreparedStatement for all queries (prevents SQL injection).
 * - Strict user_id scoping on every operation (multi-tenant security).
 * - Upsert pattern: one entry per date, saving a second entry on that date updates it.
 * - Validation prior to persistence.
 */
public class MoodEntryDAO implements Dao<MoodEntry> {

    @Override
    public MoodEntry create(MoodEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("MoodEntry cannot be null.");
        }
        entry.validate();

        String sql = "INSERT INTO mood_entries (user_id, entry_date, mood, symptoms, pain_level, sleep_hours, water_intake) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, entry.getUserId());
            ps.setString(2, entry.getEntryDate().toString());
            ps.setString(3, entry.getMood().name());
            ps.setString(4, entry.getSymptoms());
            ps.setInt(5, entry.getPainLevel());
            ps.setDouble(6, entry.getSleepHours());
            ps.setInt(7, entry.getWaterIntake());

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    entry.setId(rs.getInt(1));
                }
            }
            return entry;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert mood entry: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean update(MoodEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("MoodEntry cannot be null.");
        }
        entry.validate();

        String sql = "UPDATE mood_entries SET mood = ?, symptoms = ?, pain_level = ?, sleep_hours = ?, water_intake = ? " +
                     "WHERE entry_id = ? AND user_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, entry.getMood().name());
            ps.setString(2, entry.getSymptoms());
            ps.setInt(3, entry.getPainLevel());
            ps.setDouble(4, entry.getSleepHours());
            ps.setInt(5, entry.getWaterIntake());
            ps.setInt(6, entry.getId());
            ps.setInt(7, entry.getUserId());

            int rows = ps.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update mood entry: " + e.getMessage(), e);
        }
    }

    /**
     * Core Member 5 requirement:
     * One entry per date; a second one updates the existing record.
     */
    public MoodEntry upsert(MoodEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("MoodEntry cannot be null.");
        }
        entry.validate();

        MoodEntry existing = findByDate(entry.getEntryDate(), entry.getUserId());
        if (existing != null) {
            entry.setId(existing.getId());
            update(entry);
            return entry;
        } else {
            return create(entry);
        }
    }

    @Override
    public MoodEntry findById(int id, int userId) {
        String sql = "SELECT entry_id, user_id, entry_date, mood, symptoms, pain_level, sleep_hours, water_intake " +
                     "FROM mood_entries WHERE entry_id = ? AND user_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            ps.setInt(2, userId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query mood entry by id: " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * Retrieve single date entry for a user.
     */
    public MoodEntry findByDate(LocalDate date, int userId) {
        if (date == null) return null;
        String sql = "SELECT entry_id, user_id, entry_date, mood, symptoms, pain_level, sleep_hours, water_intake " +
                     "FROM mood_entries WHERE entry_date = ? AND user_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, date.toString());
            ps.setInt(2, userId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query mood entry by date: " + e.getMessage(), e);
        }
        return null;
    }

    @Override
    public List<MoodEntry> findAll(int userId) {
        return findRecent(userId, 100);
    }

    /**
     * Member 5 database requirement: Fetch last N entries.
     */
    public List<MoodEntry> findRecent(int userId, int limit) {
        String sql = "SELECT entry_id, user_id, entry_date, mood, symptoms, pain_level, sleep_hours, water_intake " +
                     "FROM mood_entries WHERE user_id = ? ORDER BY entry_date DESC LIMIT ?";

        List<MoodEntry> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ps.setInt(2, limit > 0 ? limit : 10);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch recent mood entries: " + e.getMessage(), e);
        }
        return list;
    }

    /**
     * Member 5 database requirement: Fetch entries between two dates.
     */
    public List<MoodEntry> findBetweenDates(int userId, LocalDate start, LocalDate end) {
        String sql = "SELECT entry_id, user_id, entry_date, mood, symptoms, pain_level, sleep_hours, water_intake " +
                     "FROM mood_entries WHERE user_id = ? AND entry_date BETWEEN ? AND ? ORDER BY entry_date ASC";

        List<MoodEntry> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ps.setString(2, start.toString());
            ps.setString(3, end.toString());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch mood entries between dates: " + e.getMessage(), e);
        }
        return list;
    }

    /**
     * Member 5 database requirement: Averages of sleep, water, and pain (for Member 6).
     */
    public MoodAverages getAverages(int userId) {
        String sql = "SELECT COUNT(*) AS total_entries, " +
                     "AVG(sleep_hours) AS avg_sleep, " +
                     "AVG(water_intake) AS avg_water, " +
                     "AVG(pain_level) AS avg_pain " +
                     "FROM mood_entries WHERE user_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int count = rs.getInt("total_entries");
                    double sleep = rs.getDouble("avg_sleep");
                    double water = rs.getDouble("avg_water");
                    double pain = rs.getDouble("avg_pain");
                    return new MoodAverages(count, 
                            Math.round(sleep * 10.0) / 10.0, 
                            Math.round(water * 10.0) / 10.0, 
                            Math.round(pain * 10.0) / 10.0);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to compute mood averages: " + e.getMessage(), e);
        }
        return new MoodAverages(0, 0.0, 0.0, 0.0);
    }

    /**
     * Keyword search within mood entries for Global Search.
     */
    public List<MoodEntry> search(int userId, String keyword) {
        String sql = "SELECT entry_id, user_id, entry_date, mood, symptoms, pain_level, sleep_hours, water_intake " +
                     "FROM mood_entries WHERE user_id = ? " +
                     "AND (LOWER(mood) LIKE ? OR LOWER(COALESCE(symptoms, '')) LIKE ? OR entry_date LIKE ?) " +
                     "ORDER BY entry_date DESC LIMIT 20";

        List<MoodEntry> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            String term = "%" + keyword.toLowerCase().trim() + "%";
            ps.setInt(1, userId);
            ps.setString(2, term);
            ps.setString(3, term);
            ps.setString(4, term);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to search mood entries: " + e.getMessage(), e);
        }
        return list;
    }

    @Override
    public boolean delete(int id, int userId) {
        String sql = "DELETE FROM mood_entries WHERE entry_id = ? AND user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            ps.setInt(2, userId);

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete mood entry: " + e.getMessage(), e);
        }
    }

    public boolean deleteByDate(LocalDate date, int userId) {
        String sql = "DELETE FROM mood_entries WHERE entry_date = ? AND user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, date.toString());
            ps.setInt(2, userId);

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete mood entry by date: " + e.getMessage(), e);
        }
    }

    private MoodEntry mapRow(ResultSet rs) throws SQLException {
        MoodEntry entry = new MoodEntry();
        entry.setId(rs.getInt("entry_id"));
        entry.setUserId(rs.getInt("user_id"));
        entry.setEntryDate(LocalDate.parse(rs.getString("entry_date")));
        entry.setMood(Mood.fromString(rs.getString("mood")));
        entry.setSymptoms(rs.getString("symptoms"));
        entry.setPainLevel(rs.getInt("pain_level"));
        entry.setSleepHours(rs.getDouble("sleep_hours"));
        entry.setWaterIntake(rs.getInt("water_intake"));
        return entry;
    }
}
