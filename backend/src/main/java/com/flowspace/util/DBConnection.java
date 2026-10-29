package com.flowspace.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Database connection manager for FlowSpace.
 * Reads configuration from environment variables or falls back to local SQLite.
 */
public class DBConnection {
    private static String customJdbcUrl = null;

    /**
     * Override database URL (useful for in-memory JUnit tests: "jdbc:sqlite::memory:").
     */
    public static void setCustomJdbcUrl(String url) {
        customJdbcUrl = url;
    }

    public static Connection getConnection() throws SQLException {
        if (customJdbcUrl != null) {
            return DriverManager.getConnection(customJdbcUrl);
        }

        String dbUrl = System.getenv("DB_URL");
        if (dbUrl == null || dbUrl.trim().isEmpty()) {
            dbUrl = System.getProperty("flowspace.db.url", "jdbc:sqlite:flowspace.db");
        }

        String user = System.getenv("DB_USER");
        String pass = System.getenv("DB_PASSWORD");

        if (user != null && pass != null) {
            return DriverManager.getConnection(dbUrl, user, pass);
        } else {
            return DriverManager.getConnection(dbUrl);
        }
    }

    /**
     * Helper to initialize schema tables if not already present.
     */
    public static void initializeSchema(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "user_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "name TEXT NOT NULL," +
                    "email TEXT NOT NULL UNIQUE," +
                    "password_hash TEXT NOT NULL," +
                    "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP);");

            stmt.execute("CREATE TABLE IF NOT EXISTS notes (" +
                    "note_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE," +
                    "title TEXT NOT NULL," +
                    "content TEXT NOT NULL," +
                    "category TEXT NOT NULL DEFAULT 'General'," +
                    "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    "updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP);");

            stmt.execute("CREATE TABLE IF NOT EXISTS tasks (" +
                    "task_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE," +
                    "title TEXT NOT NULL," +
                    "due_date TEXT NOT NULL," +
                    "priority TEXT NOT NULL DEFAULT 'MEDIUM'," +
                    "status TEXT NOT NULL DEFAULT 'TODO'," +
                    "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP);");

            stmt.execute("CREATE TABLE IF NOT EXISTS mood_entries (" +
                    "entry_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE," +
                    "entry_date TEXT NOT NULL," +
                    "mood TEXT NOT NULL," +
                    "symptoms TEXT," +
                    "pain_level INTEGER NOT NULL DEFAULT 0," +
                    "sleep_hours REAL NOT NULL DEFAULT 0," +
                    "water_intake INTEGER NOT NULL DEFAULT 0," +
                    "UNIQUE (user_id, entry_date));");
        }
    }
}
