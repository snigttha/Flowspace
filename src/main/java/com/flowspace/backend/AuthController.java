
package com.flowspace.backend;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @RequestBody Map<String, String> data) {

        try {
            User user = authService.register(
                data.get("name"),
                data.get("email"),
                data.get("password")
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(
                Map.of(
                    "message", "Registration successful",
                    "id", user.getUserId(),
                    "userId", user.getUserId(),
                    "name", user.getName(),
                    "email", user.getEmail()
                )
            );

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                Map.of("error", e.getMessage())
            );

        } catch (Exception e) {
            return ResponseEntity.status(
                HttpStatus.INTERNAL_SERVER_ERROR
            ).body(Map.of("error", "Registration failed"));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody Map<String, String> data,
            HttpSession session,
            HttpServletRequest request) {

        try {
            User user = authService.login(
                data.get("email"),
                data.get("password")
            );

            // Rotate the session ID after successful authentication.
            request.changeSessionId();

            session.setAttribute("userId", user.getUserId());

            return ResponseEntity.ok(
                Map.of(
                    "message", "Login successful",
                    "id", user.getUserId(),
                    "userId", user.getUserId(),
                    "name", user.getName(),
                    "email", user.getEmail()
                )
            );

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(
                HttpStatus.UNAUTHORIZED
            ).body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity.status(
                HttpStatus.INTERNAL_SERVER_ERROR
            ).body(Map.of("error", "Login failed"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {

        session.invalidate();

        return ResponseEntity.ok(
            Map.of("message", "Logout successful")
        );
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpSession session) {

        Object value = session.getAttribute("userId");

        if (!(value instanceof Number)) {
            return ResponseEntity.status(
                HttpStatus.UNAUTHORIZED
            ).body(Map.of("error", "Please log in"));
        }

        try {
            int userId = ((Number) value).intValue();
            User user = authService.getUser(userId);

            if (user == null) {
                return ResponseEntity.status(
                    HttpStatus.NOT_FOUND
                ).body(Map.of("error", "User not found"));
            }

            return ResponseEntity.ok(
                Map.of(
                    "id", user.getUserId(),
                    "userId", user.getUserId(),
                    "name", user.getName(),
                    "email", user.getEmail()
                )
            );

        } catch (Exception e) {
            return ResponseEntity.status(
                HttpStatus.INTERNAL_SERVER_ERROR
            ).body(Map.of("error", "Could not load account"));
        }
    }

    @PostMapping("/settings/name")
    public ResponseEntity<?> updateName(
            @RequestBody Map<String, String> data,
            HttpSession session) {

        Object value = session.getAttribute("userId");

        if (!(value instanceof Number)) {
            return ResponseEntity.status(
                HttpStatus.UNAUTHORIZED
            ).body(Map.of("error", "Please log in"));
        }

        try {
            int userId = ((Number) value).intValue();

            User user = authService.updateName(
                userId,
                data.get("name")
            );

            return ResponseEntity.ok(
                Map.of(
                    "message", "Name updated",
                    "name", user.getName(),
                    "email", user.getEmail()
                )
            );

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                Map.of("error", e.getMessage())
            );

        } catch (Exception e) {
            return ResponseEntity.status(
                HttpStatus.INTERNAL_SERVER_ERROR
            ).body(Map.of("error", "Could not update name"));
        }
    }

    @PostMapping("/settings/password")
    public ResponseEntity<?> changePassword(
            @RequestBody Map<String, String> data,
            HttpSession session) {

        Object value = session.getAttribute("userId");

        if (!(value instanceof Number)) {
            return ResponseEntity.status(
                HttpStatus.UNAUTHORIZED
            ).body(Map.of("error", "Please log in"));
        }

        try {
            int userId = ((Number) value).intValue();

            authService.changePassword(
                userId,
                data.get("currentPassword"),
                data.get("newPassword")
            );

            return ResponseEntity.ok(
                Map.of("message", "Password changed successfully")
            );

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                Map.of("error", e.getMessage())
            );

        } catch (Exception e) {
            return ResponseEntity.status(
                HttpStatus.INTERNAL_SERVER_ERROR
            ).body(Map.of("error", "Could not change password"));
        }
    }
}
