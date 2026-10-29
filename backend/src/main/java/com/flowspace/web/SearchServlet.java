package com.flowspace.web;

import com.flowspace.service.SearchService;
import com.google.gson.Gson;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

/**
 * REST Endpoint for Global Search: GET /api/search?q=...
 * Owned by Member 5.
 * 
 * Invokes SearchService to perform polymorphic retrieval across Notes, Tasks, and Moods.
 */
@WebServlet(name = "SearchServlet", urlPatterns = {"/api/search"})
public class SearchServlet extends HttpServlet {
    private final SearchService searchService = new SearchService();
    private final Gson gson = new Gson();

    private int getAuthenticatedUserId(HttpServletRequest req) {
        Object sessionUid = req.getSession().getAttribute("userId");
        if (sessionUid instanceof Integer) {
            return (Integer) sessionUid;
        }
        String headerUid = req.getHeader("X-User-Id");
        if (headerUid != null) {
            try { return Integer.parseInt(headerUid); } catch (NumberFormatException ignored) {}
        }
        return 1;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        int userId = getAuthenticatedUserId(req);
        String query = req.getParameter("q");

        if (query == null || query.trim().isEmpty()) {
            resp.getWriter().write("[]");
            return;
        }

        try {
            List<SearchService.SearchResult> results = searchService.search(userId, query);
            resp.getWriter().write(gson.toJson(results));
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\": \"Search error: " + e.getMessage() + "\"}");
        }
    }
}
