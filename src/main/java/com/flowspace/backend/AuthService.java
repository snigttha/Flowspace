
package com.flowspace.backend;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserDAO userDAO;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthService(UserDAO userDAO) {
        this.userDAO = userDAO;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    public User register(String name, String email, String password)
            throws Exception {

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }

        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException(
                "Password must contain at least 8 characters"
            );
        }

        name = name.trim();
        email = email.trim().toLowerCase();

        if (userDAO.emailExists(email)) {
            throw new IllegalArgumentException(
                "An account with this email already exists"
            );
        }

        String hashedPassword = passwordEncoder.encode(password);

        User user = new User(name, email, hashedPassword);

        return userDAO.save(user);
    }

    public User login(String email, String password) throws Exception {

        if (email == null || email.isBlank()
                || password == null || password.isEmpty()) {
            throw new IllegalArgumentException(
                "Email and password are required"
            );
        }

        email = email.trim().toLowerCase();

        User user = userDAO.findByEmail(email);

        if (user == null
                || !passwordEncoder.matches(
                    password, user.getPasswordHash()
                )) {
            throw new IllegalArgumentException(
                "Invalid email or password"
            );
        }

        return user;
    }
    
    public User updateName(int userId, String name) throws Exception {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }

        name = name.trim();
        userDAO.updateName(userId, name);

        return userDAO.findById(userId);
    }

    public void changePassword(
            int userId,
            String currentPassword,
            String newPassword) throws Exception {

        User user = userDAO.findById(userId);

        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

        if (currentPassword == null || currentPassword.isEmpty()
                || !passwordEncoder.matches(
                    currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException(
                "Current password is incorrect");
        }

        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException(
                "New password must contain at least 8 characters");
        }

        String hashedPassword = passwordEncoder.encode(newPassword);
        userDAO.updatePasswordHash(userId, hashedPassword);
    }
    
    public User getUser(int userId) throws Exception {
        return userDAO.findById(userId);
    }


}
