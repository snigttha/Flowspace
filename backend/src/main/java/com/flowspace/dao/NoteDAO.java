package com.flowspace.dao;

import com.flowspace.model.Note;
import com.flowspace.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Notes.
 * Provides search capability for Member 5's Global Search service.
 */
public class NoteDAO implements Dao<Note> {

    @Override
    public Note create(Note note) {
        note.validate();
        String sql = "INSERT INTO notes (user_id, title, content, category) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, note.getUserId());
            ps.setString(2, note.getTitle());
            ps.setString(3, note.getContent());
            ps.setString(4, note.getCategory());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) note.setId(rs.getInt(1));
            }
            return note;
        } catch (SQLException e) {
            throw new RuntimeException("Error inserting note: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean update(Note note) {
        note.validate();
        String sql = "UPDATE notes SET title = ?, content = ?, category = ? WHERE note_id = ? AND user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, note.getTitle());
            ps.setString(2, note.getContent());
            ps.setString(3, note.getCategory());
            ps.setInt(4, note.getId());
            ps.setInt(5, note.getUserId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Error updating note: " + e.getMessage(), e);
        }
    }

    @Override
    public Note findById(int id, int userId) {
        String sql = "SELECT note_id, user_id, title, content, category, created_at, updated_at FROM notes WHERE note_id = ? AND user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Note(rs.getInt("note_id"), rs.getInt("user_id"),
                            rs.getString("title"), rs.getString("content"),
                            rs.getString("category"), null, null);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding note: " + e.getMessage(), e);
        }
        return null;
    }

    @Override
    public List<Note> findAll(int userId) {
        return search(userId, "");
    }

    public List<Note> search(int userId, String keyword) {
        String sql = "SELECT note_id, user_id, title, content, category FROM notes " +
                     "WHERE user_id = ? AND (LOWER(title) LIKE ? OR LOWER(content) LIKE ? OR LOWER(category) LIKE ?) " +
                     "ORDER BY updated_at DESC LIMIT 20";
        List<Note> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String term = "%" + keyword.toLowerCase().trim() + "%";
            ps.setInt(1, userId);
            ps.setString(2, term);
            ps.setString(3, term);
            ps.setString(4, term);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Note(rs.getInt("note_id"), rs.getInt("user_id"),
                            rs.getString("title"), rs.getString("content"),
                            rs.getString("category"), null, null));
                }
            }
        } catch (SQLException e) {
            // If table doesn't exist yet, return empty list safely
            return list;
        }
        return list;
    }

    @Override
    public boolean delete(int id, int userId) {
        String sql = "DELETE FROM notes WHERE note_id = ? AND user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting note: " + e.getMessage(), e);
        }
    }
}
