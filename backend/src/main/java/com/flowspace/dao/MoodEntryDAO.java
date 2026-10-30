package com.flowspace.dao;

import com.flowspace.db.DBConnection;
import com.flowspace.model.MoodEntry;
import com.flowspace.model.MoodEntry.Mood;
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

    public List<MoodEntry> findRecent(int userId, int limit) throws SQLException {
        List<MoodEntry> list = new ArrayList<>();
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(SQL_FIND_RECENT)) {
            ps.setInt(1, userId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
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
