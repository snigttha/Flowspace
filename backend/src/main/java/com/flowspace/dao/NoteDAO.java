package com.flowspace.dao;

import com.flowspace.db.DBConnection;
import com.flowspace.model.Note;
import com.flowspace.model.Note.Category;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for the {@code notes} table.
 *
 * <p>Implements {@link Dao}{@code <Note>} and adds note-specific
 * query methods (keyword search, category filter).
 *
 * <p>Security: <strong>every</strong> query filters by {@code user_id}
 * using a {@code PreparedStatement} so one user can never read or modify
 * another user's notes (PLAN.md team rule).
 *
 * <p>Index used: {@code idx_notes_user_cat} on {@code (user_id, category)}.
 *
 * @author Member 2 – Notes module
 */
public class NoteDAO implements Dao<Note> {

    // ── SQL statements ───────────────────────────────────────────────────

    private static final String SQL_INSERT =
        "INSERT INTO notes (user_id, title, content, category) VALUES (?, ?, ?, ?)";

    private static final String SQL_FIND_BY_ID =
        "SELECT * FROM notes WHERE note_id = ? AND user_id = ?";

    private static final String SQL_FIND_ALL =
        "SELECT * FROM notes WHERE user_id = ? ORDER BY updated_at DESC";

    private static final String SQL_UPDATE =
        "UPDATE notes SET title = ?, content = ?, category = ? WHERE note_id = ? AND user_id = ?";

    private static final String SQL_DELETE =
        "DELETE FROM notes WHERE note_id = ? AND user_id = ?";

    /** Keyword search: checks title and content, filtered by user_id. */
    private static final String SQL_SEARCH =
        "SELECT * FROM notes WHERE user_id = ? " +
        "AND (LOWER(title) LIKE ? OR LOWER(content) LIKE ?) " +
        "ORDER BY updated_at DESC";

    /** Category filter (uses idx_notes_user_cat). */
    private static final String SQL_BY_CATEGORY =
        "SELECT * FROM notes WHERE user_id = ? AND category = ? ORDER BY updated_at DESC";

    /** Combined keyword search + category filter. */
    private static final String SQL_SEARCH_AND_CATEGORY =
        "SELECT * FROM notes WHERE user_id = ? AND category = ? " +
        "AND (LOWER(title) LIKE ? OR LOWER(content) LIKE ?) " +
        "ORDER BY updated_at DESC";

    // ── Connection hook (overridable for testing) ────────────────────────

    /**
     * Opens a JDBC connection. Subclasses (e.g. test doubles) may override
     * this to supply an in-memory connection instead of the real database.
     */
    protected Connection openConnection() throws SQLException {
        return DBConnection.get();
    }

    // ── Dao<Note> implementation ─────────────────────────────────────────

    /**
     * Inserts a new note row and returns the note with the generated
     * {@code note_id}, {@code created_at} and {@code updated_at} set.
     */
    @Override
    public Note create(Note note) throws SQLException {
        note.validate();
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(
                 SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt   (1, note.getUserId());
            ps.setString(2, note.getTitle());
            ps.setString(3, note.getContent());
            ps.setString(4, note.getCategory().name());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int generated = keys.getInt(1);
                    // Re-fetch so we get the server-stamped timestamps
                    return findById(generated, note.getUserId())
                        .orElseThrow(() -> new SQLException("Insert succeeded but row not found"));
                }
            }
        }
        throw new SQLException("Insert did not return a generated key");
    }

    @Override
    public Optional<Note> findById(int id, int userId) throws SQLException {
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
    public List<Note> findAll(int userId) throws SQLException {
        List<Note> list = new ArrayList<>();
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(SQL_FIND_ALL)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    /**
     * Updates title, content and category for an existing note.
     * Validates the note before writing.
     */
    @Override
    public Optional<Note> update(Note note) throws SQLException {
        note.validate();
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(SQL_UPDATE)) {
            ps.setString(1, note.getTitle());
            ps.setString(2, note.getContent());
            ps.setString(3, note.getCategory().name());
            ps.setInt   (4, note.getId());
            ps.setInt   (5, note.getUserId());
            int affected = ps.executeUpdate();
            if (affected == 0) return Optional.empty();
        }
        return findById(note.getId(), note.getUserId());
    }

    /**
     * Deletes the note only when it belongs to the given user.
     *
     * @return true if a row was deleted (false = not found or wrong user)
     */
    @Override
    public boolean delete(int id, int userId) throws SQLException {
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(SQL_DELETE)) {
            ps.setInt(1, id);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    // ── Extra NoteDAO-specific queries ───────────────────────────────────

    /**
     * Full-text keyword search across title and content.
     * Uses SQL {@code LIKE} with {@code LOWER()} for case-insensitive matching.
     * Scoped to {@code userId} so data isolation is preserved.
     *
     * @param userId  the logged-in user
     * @param keyword the search term (partial matches accepted)
     * @return matching notes, newest first
     */
    public List<Note> search(int userId, String keyword) throws SQLException {
        List<Note> list = new ArrayList<>();
        String pattern = "%" + keyword.toLowerCase() + "%";
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(SQL_SEARCH)) {
            ps.setInt   (1, userId);
            ps.setString(2, pattern);
            ps.setString(3, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    /**
     * Returns all notes in the given category for the given user.
     * Uses {@code idx_notes_user_cat} index.
     *
     * @param userId   the logged-in user
     * @param category the category to filter on
     */
    public List<Note> findByCategory(int userId, Category category) throws SQLException {
        List<Note> list = new ArrayList<>();
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(SQL_BY_CATEGORY)) {
            ps.setInt   (1, userId);
            ps.setString(2, category.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    /**
     * Combined keyword search + category filter.
     *
     * @param userId   the logged-in user
     * @param keyword  the search term
     * @param category the category to restrict to
     */
    public List<Note> searchByCategory(int userId, String keyword,
                                       Category category) throws SQLException {
        List<Note> list = new ArrayList<>();
        String pattern = "%" + keyword.toLowerCase() + "%";
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(SQL_SEARCH_AND_CATEGORY)) {
            ps.setInt   (1, userId);
            ps.setString(2, category.name());
            ps.setString(3, pattern);
            ps.setString(4, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    // ── ResultSet → Note mapper ──────────────────────────────────────────

    /** Maps one ResultSet row to a Note object. */
    private Note map(ResultSet rs) throws SQLException {
        Note n = new Note();
        n.setId     (rs.getInt   ("note_id"));
        n.setUserId (rs.getInt   ("user_id"));
        n.setTitle  (rs.getString("title"));
        n.setContent(rs.getString("content"));
        n.setCategory(Category.fromString(rs.getString("category")));

        String created = rs.getString("created_at");
        if (created != null) n.setCreatedAt(LocalDateTime.parse(
            created.replace(" ", "T").substring(0, 19)));

        String updated = rs.getString("updated_at");
        if (updated != null) n.setUpdatedAt(LocalDateTime.parse(
            updated.replace(" ", "T").substring(0, 19)));

        return n;
    }
}
