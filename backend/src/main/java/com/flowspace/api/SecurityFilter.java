package com.flowspace.api;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

/**
 * Global HTTP Security Filter.
 *
 * <p>Enforces:
 * <ul>
 *   <li>Modern defensive HTTP security headers (nosniff, SAMEORIGIN, Referrer-Policy, CSP).</li>
 *   <li>Same-Origin CSRF protection on state-modifying requests (POST, PUT, DELETE).</li>
 * </ul>
 *
 * @author FlowSpace Security Lead
 */
public class SecurityFilter implements Filter {

    private static final Set<String> STATE_CHANGING_METHODS = Set.of("POST", "PUT", "DELETE", "PATCH");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest req) || !(response instanceof HttpServletResponse resp)) {
            chain.doFilter(request, response);
            return;
        }

        // 1. Inject Defensive Security Headers
        resp.setHeader("X-Content-Type-Options", "nosniff");
        resp.setHeader("X-Frame-Options", "SAMEORIGIN");
        resp.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        resp.setHeader("Content-Security-Policy",
            "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self';");

        // 2. CSRF & Cross-Origin Validation for State-Changing API Endpoints
        String method = req.getMethod().toUpperCase();
        String path = req.getRequestURI();

        if (STATE_CHANGING_METHODS.contains(method) && path.startsWith("/api/")) {
            if (!isValidOrigin(req)) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                resp.setContentType("application/json;charset=UTF-8");
                resp.getWriter().print("{\"error\":\"Forbidden: Cross-site request rejected.\"}");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * Verifies that the Origin or Referer header matches the application's host.
     */
    private boolean isValidOrigin(HttpServletRequest req) {
        String origin = req.getHeader("Origin");
        if (origin != null && !origin.isBlank()) {
            return matchesHost(origin, req);
        }

        String referer = req.getHeader("Referer");
        if (referer != null && !referer.isBlank()) {
            return matchesHost(referer, req);
        }

        // When neither is provided (e.g., standard browser same-site POST, curl, or same-origin programmatic API call)
        // verify custom header or Content-Type is application/json
        String requestedWith = req.getHeader("X-Requested-With");
        String contentType = req.getContentType();
        return (requestedWith != null && !requestedWith.isBlank()) ||
               (contentType != null && contentType.toLowerCase().contains("application/json")) ||
               req.getHeader("Sec-Fetch-Site") == null ||
               "same-origin".equalsIgnoreCase(req.getHeader("Sec-Fetch-Site")) ||
               "none".equalsIgnoreCase(req.getHeader("Sec-Fetch-Site"));
    }

    private boolean matchesHost(String headerUrl, HttpServletRequest req) {
        try {
            URI uri = URI.create(headerUrl);
            String headerHost = uri.getHost();
            if (headerHost == null) return false;

            String serverName = req.getServerName();
            if (headerHost.equalsIgnoreCase(serverName)) {
                return true;
            }

            // Allow localhost / 127.0.0.1 equivalence in local development
            boolean isReqLocal = "localhost".equalsIgnoreCase(serverName) || "127.0.0.1".equals(serverName);
            boolean isHeaderLocal = "localhost".equalsIgnoreCase(headerHost) || "127.0.0.1".equals(headerHost);
            return isReqLocal && isHeaderLocal;
        } catch (Exception e) {
            return false;
        }
    }
}

