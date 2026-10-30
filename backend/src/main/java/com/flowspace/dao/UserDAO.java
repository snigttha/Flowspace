package com.flowspace.dao;

import com.flowspace.db.DBConnection;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.*;

/**
 * Data Access Object for the {@code users} table in the backend servlet environment.
 * Uses BCrypt hashing for secure password storage.
 *
 * @author Member 6 (Workspace & Integration Lead)
 */
public class UserDAO {

    public static class UserRecord {
        private int userId;
        private String name;
        private String email;
        private String passwordHash;

        public UserRecord() {}

        public UserRecord(int userId, String name, String email, String passwordHash) {
            this.userId = userId;
            this.name = name;
            this.email = email;
            this.passwordHash = passwordHash;
        }

        public int getUserId() { return userId; }
        public void setUserId(int userId) { this.userId = userId; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getPasswordHash() { return passwordHash; }
        public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    }

    protected Connection openConnection() throws SQLException {
        return DBConnection.get();
    }

    public boolean emailExists(String email) throws SQLException {
        String sql = "SELECT user_id FROM users WHERE LOWER(email) = LOWER(?)";
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public UserRecord findByEmail(String email) throws SQLException {
        String sql = "SELECT user_id, name, email, password_hash FROM users WHERE LOWER(email) = LOWER(?)";
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new UserRecord(
                        rs.getInt("user_id"),
                        rs.getString("name"),
                        rs.getString("email"),
                        rs.getString("password_hash")
                    );
                }
            }
        }
        return null;
    }

    public UserRecord findById(int userId) throws SQLException {
        String sql = "SELECT user_id, name, email, password_hash FROM users WHERE user_id = ?";
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new UserRecord(
                        rs.getInt("user_id"),
                        rs.getString("name"),
                        rs.getString("email"),
                        rs.getString("password_hash")
                    );
                }
            }
        }
        return null;
    }

    public UserRecord create(String name, String email, String rawPassword) throws SQLException {
        if (name == null || name.trim().length() < 2 || name.trim().length() > 60) {
            throw new IllegalArgumentException("Name must be between 2 and 60 characters.");
        }
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("A valid email address is required.");
        }
        if (rawPassword == null || rawPassword.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters.");
        }

        String hash = BCrypt.hashpw(rawPassword, BCrypt.gensalt());
        String sql = "INSERT INTO users (name, email, password_hash) VALUES (?, ?, ?)";

        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name.trim());
            ps.setString(2, email.trim().toLowerCase());
            ps.setString(3, hash);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return new UserRecord(keys.getInt(1), name.trim(), email.trim().toLowerCase(), hash);
                }
            }
        }
        throw new SQLException("Failed to create user");
    }

    public UserRecord authenticate(String email, String rawPassword) throws SQLException {
        UserRecord u = findByEmail(email);
        if (u == null) return null;
        if (BCrypt.checkpw(rawPassword, u.getPasswordHash())) {
            return u;
        }
        return null;
    }
}

