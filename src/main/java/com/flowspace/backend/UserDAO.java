
package com.flowspace.backend;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.springframework.stereotype.Repository;

@Repository
public class UserDAO {

    private final DataSource dataSource;

    public UserDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public boolean emailExists(String email) throws Exception {
        String sql = "SELECT user_id FROM users WHERE email = ?";

        try (
            Connection connection = dataSource.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, email);

            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    public User findByEmail(String email) throws Exception {
        String sql = """
            SELECT user_id, name, email, password_hash
            FROM users
            WHERE email = ?
            """;

        try (
            Connection connection = dataSource.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, email);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    User user = new User();

                    user.setUserId(result.getInt("user_id"));
                    user.setName(result.getString("name"));
                    user.setEmail(result.getString("email"));
                    user.setPasswordHash(
                        result.getString("password_hash")
                    );

                    return user;
                }
            }
        }

        return null;
    }

    public User save(User user) throws Exception {
        String sql = """
            INSERT INTO users (name, email, password_hash)
            VALUES (?, ?, ?)
            """;

        try (
            Connection connection = dataSource.getConnection();
            PreparedStatement statement = connection.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS
            )
        ) {
            statement.setString(1, user.getName());
            statement.setString(2, user.getEmail());
            statement.setString(3, user.getPasswordHash());

            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    user.setUserId(keys.getInt(1));
                }
            }

            return user;
        }
    }
    
public User findById(int userId) throws Exception {
    String sql = """
        SELECT user_id, name, email, password_hash
        FROM users
        WHERE user_id = ?
        """;

    try (Connection connection = dataSource.getConnection();
         PreparedStatement statement = connection.prepareStatement(sql)) {

        statement.setInt(1, userId);

        try (ResultSet result = statement.executeQuery()) {
            if (result.next()) {
                User user = new User();
                user.setUserId(result.getInt("user_id"));
                user.setName(result.getString("name"));
                user.setEmail(result.getString("email"));
                user.setPasswordHash(result.getString("password_hash"));
                return user;
            }
        }
    }

    return null;
}

    public void updateName(int userId, String name) throws Exception {
        String sql = "UPDATE users SET name = ? WHERE user_id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, name);
            statement.setInt(2, userId);
            statement.executeUpdate();
        }
    }

    public void updatePasswordHash(int userId, String passwordHash)
            throws Exception {
        String sql = "UPDATE users SET password_hash = ? WHERE user_id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, passwordHash);
            statement.setInt(2, userId);
            statement.executeUpdate();
        }
    }

}

