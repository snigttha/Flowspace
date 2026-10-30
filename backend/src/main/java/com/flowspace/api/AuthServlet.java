package com.flowspace.api;

import com.flowspace.dao.UserDAO;
import com.flowspace.dao.UserDAO.UserRecord;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Authentication REST Servlet for user registration, login, logout, and session checks.
 *
 * <p>Endpoints:
 * <pre>
 *   POST /api/register
 *   POST /api/login
 *   POST /api/logout
 *   GET  /api/me
 * </pre>
 *
 * @author Member 6 (Workspace & Integration Lead)
 */
@WebServlet(urlPatterns = {"/api/register", "/api/login", "/api/logout", "/api/me"})
public class AuthServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final UserDAO userDAO = new UserDAO();
    private final Gson gson = new Gson();

    // In-memory brute force rate limiters (5 attempts / 60 seconds)
    private final RateLimiter loginLimiter = new RateLimiter(5, 60_000);
    private final RateLimiter registerLimiter = new RateLimiter(5, 60_000);

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        String path = req.getServletPath();
        if ("/api/me".equals(path)) {
            handleMe(req, resp);
        } else {
            resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        String path = req.getServletPath();
        switch (path) {
            case "/api/register" -> handleRegister(req, resp);
            case "/api/login"    -> handleLogin(req, resp);
            case "/api/logout"   -> handleLogout(req, resp);
            default              -> resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private void handleMe(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Please log in");
            return;
        }

        int userId = (int) session.getAttribute("userId");
        try {
            UserRecord u = userDAO.findById(userId);
            if (u == null) {
                sendError(resp, HttpServletResponse.SC_NOT_FOUND, "User not found");
                return;
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("userId", u.getUserId());
            out.put("name", u.getName());
            out.put("email", u.getEmail());
            sendJson(resp, HttpServletResponse.SC_OK, out);
        } catch (SQLException e) {
            System.err.println("Database error in /api/me: " + e.getMessage());
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An internal error occurred. Please try again.");
        }
    }

    private void handleRegister(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String clientIp = getClientIp(req);
        if (!registerLimiter.isAllowed(clientIp)) {
            sendError(resp, 429, "Too many registration attempts. Please wait a minute and try again.");
            return;
        }

        JsonObject body = parseBody(req, resp);
        if (body == null) return;

        String name = jsonString(body, "name");
        String email = jsonString(body, "email");
        String password = jsonString(body, "password");

        try {
            if (userDAO.emailExists(email)) {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "An account with this email already exists.");
                return;
            }
            UserRecord created = userDAO.create(name, email, password);
            HttpSession session = req.getSession(true);
            session.setAttribute("userId", created.getUserId());

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("message", "Registration successful");
            out.put("userId", created.getUserId());
            out.put("name", created.getName());
            out.put("email", created.getEmail());
            sendJson(resp, HttpServletResponse.SC_CREATED, out);
        } catch (IllegalArgumentException e) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (SQLException e) {
            System.err.println("Registration database failure: " + e.getMessage());
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Registration failed. Please check your information and try again.");
        }
    }

    private void handleLogin(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String clientIp = getClientIp(req);
        if (loginLimiter.isBlocked(clientIp)) {
            sendError(resp, 429, "Too many failed login attempts. Please wait a minute and try again.");
            return;
        }

        JsonObject body = parseBody(req, resp);
        if (body == null) return;

        String email = jsonString(body, "email");
        String password = jsonString(body, "password");

        try {
            UserRecord user = userDAO.authenticate(email, password);
            if (user == null) {
                loginLimiter.recordFailure(clientIp);
                sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Email or password is incorrect.");
                return;
            }

            // Successful authentication: clear failure count
            loginLimiter.reset(clientIp);

            if (req.getSession(false) != null) {
                req.changeSessionId();
            }
            HttpSession session = req.getSession(true);
            session.setAttribute("userId", user.getUserId());

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("message", "Login successful");
            out.put("userId", user.getUserId());
            out.put("name", user.getName());
            out.put("email", user.getEmail());
            sendJson(resp, HttpServletResponse.SC_OK, out);
        } catch (SQLException e) {
            System.err.println("Login database failure: " + e.getMessage());
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Login failed due to an internal error. Please try again.");
        }
    }

    private void handleLogout(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        sendJson(resp, HttpServletResponse.SC_OK, Map.of("message", "Logout successful"));
    }

    private JsonObject parseBody(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            return JsonParser.parseReader(req.getReader()).getAsJsonObject();
        } catch (Exception e) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid JSON format in request body.");
            return null;
        }
    }

    private String getClientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return req.getRemoteAddr() != null ? req.getRemoteAddr() : "unknown";
    }

    private String jsonString(JsonObject obj, String key) {
        return (obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsString() : null;
    }

    private void sendJson(HttpServletResponse resp, int status, Object data) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        try (PrintWriter w = resp.getWriter()) {
            w.print(gson.toJson(data));
        }
    }

    private void sendError(HttpServletResponse resp, int status, String message) throws IOException {
        sendJson(resp, status, Map.of("error", message, "message", message));
    }
}

