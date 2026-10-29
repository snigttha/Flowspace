package com.flowspace.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Shared database connection helper.
 *
 * <p>Reads credentials from environment variables so secrets are never
 * committed to source control (PLAN.md security rule).
 *
 * <p>Environment variables:
 * <pre>
 *   DB_URL      – JDBC URL, e.g. {@code jdbc:sqlite:flowspace.db}
 *                 or {@code jdbc:mysql://localhost:3306/flowspace}
 *   DB_USER     – database username  (ignored for SQLite)
 *   DB_PASSWORD – database password  (ignored for SQLite)
 * </pre>
 *
 * <p>When {@code DB_URL} is not set the class falls back to a local
 * SQLite file in the project root so the app runs out of the box
 * without any configuration.
 *
 * @author Member 1 (stub created by Member 2; maintained by Member 6)
 */
public class DBConnection {

    private static final String DEFAULT_URL = "jdbc:sqlite:flowspace.db";
    private static volatile boolean schemaInitialized = false;

    private DBConnection() {}

    /**
     * Returns a new JDBC connection for every call.
     * Callers must close it (use try-with-resources).
     */
    public static Connection get() throws SQLException {
        String url      = env("DB_URL",      DEFAULT_URL);
        String user     = env("DB_USER",     null);
        String password = env("DB_PASSWORD", null);

        Connection conn;
        if (user != null && password != null) {
            conn = DriverManager.getConnection(url, user, password);
        } else {
            conn = DriverManager.getConnection(url);
        }

        if (!schemaInitialized && url.startsWith("jdbc:sqlite:") && !url.contains("mode=memory")) {
            synchronized (DBConnection.class) {
                if (!schemaInitialized) {
                    initSqliteSchema(conn);
                    schemaInitialized = true;
                }
            }
        }

        return conn;
    }

    private static void initSqliteSchema(Connection conn) {
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON;");
            st.execute("""
                CREATE TABLE IF NOT EXISTS users (
                  user_id       INTEGER PRIMARY KEY AUTOINCREMENT,
                  name          TEXT NOT NULL CHECK (length(name) BETWEEN 2 AND 60),
                  email         TEXT NOT NULL UNIQUE,
                  password_hash TEXT NOT NULL,
                  created_at    TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                );""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS notes (
                  note_id    INTEGER PRIMARY KEY AUTOINCREMENT,
                  user_id    INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
                  title      TEXT NOT NULL CHECK (length(title) <= 100),
                  content    TEXT NOT NULL,
                  category   TEXT NOT NULL DEFAULT 'General',
                  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                );""");
            st.execute("CREATE INDEX IF NOT EXISTS idx_notes_user_cat ON notes(user_id, category);");
            st.execute("""
                CREATE TABLE IF NOT EXISTS tasks (
                  task_id    INTEGER PRIMARY KEY AUTOINCREMENT,
                  user_id    INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
                  title      TEXT NOT NULL CHECK (length(title) <= 100),
                  due_date   TEXT NOT NULL,
                  priority   TEXT NOT NULL DEFAULT 'MEDIUM' CHECK (priority IN ('LOW','MEDIUM','HIGH')),
                  status     TEXT NOT NULL DEFAULT 'TODO'   CHECK (status IN ('TODO','IN_PROGRESS','DONE')),
                  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                );""");
            st.execute("CREATE INDEX IF NOT EXISTS idx_tasks_user_status ON tasks(user_id, status);");
            st.execute("""
                CREATE TABLE IF NOT EXISTS period_logs (
                  log_id              INTEGER PRIMARY KEY AUTOINCREMENT,
                  user_id             INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
                  cycle_start_date    TEXT NOT NULL,
                  cycle_length        INTEGER NOT NULL DEFAULT 28 CHECK (cycle_length BETWEEN 15 AND 60),
                  predicted_next_date TEXT,
                  UNIQUE (user_id, cycle_start_date)
                );""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS mood_entries (
                  entry_id     INTEGER PRIMARY KEY AUTOINCREMENT,
                  user_id      INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
                  entry_date   TEXT NOT NULL,
                  mood         TEXT NOT NULL CHECK (mood IN ('HAPPY','CALM','NEUTRAL','TIRED','ANXIOUS','IRRITABLE','SAD')),
                  symptoms     TEXT,
                  pain_level   INTEGER NOT NULL DEFAULT 0 CHECK (pain_level BETWEEN 0 AND 10),
                  sleep_hours  REAL    NOT NULL DEFAULT 0 CHECK (sleep_hours BETWEEN 0 AND 24),
                  water_intake INTEGER NOT NULL DEFAULT 0 CHECK (water_intake BETWEEN 0 AND 30),
                  UNIQUE (user_id, entry_date)
                );""");
        } catch (SQLException ignored) {
            // Non-fatal if tables already created
        }
    }

    private static String env(String key, String fallback) {
        String v = System.getenv(key);
        return (v != null && !v.isBlank()) ? v : fallback;
    }
}
