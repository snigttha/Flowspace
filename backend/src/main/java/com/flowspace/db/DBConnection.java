package com.flowspace.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

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
 * @author Member 1 (stub created here by Member 2 for standalone use;
 *                   Member 1 should replace with the authoritative version)
 */
public class DBConnection {

    private static final String DEFAULT_URL = "jdbc:sqlite:flowspace.db";

    private DBConnection() {}

    /**
     * Returns a new JDBC connection for every call.
     * Callers must close it (use try-with-resources).
     */
    public static Connection get() throws SQLException {
        String url      = env("DB_URL",      DEFAULT_URL);
        String user     = env("DB_USER",     null);
        String password = env("DB_PASSWORD", null);

        if (user != null && password != null) {
            return DriverManager.getConnection(url, user, password);
        }
        return DriverManager.getConnection(url);
    }

    private static String env(String key, String fallback) {
        String v = System.getenv(key);
        return (v != null && !v.isBlank()) ? v : fallback;
    }
}
