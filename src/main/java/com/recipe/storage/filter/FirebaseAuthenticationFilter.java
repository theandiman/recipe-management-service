package com.recipe.storage.filter;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter that validates Firebase ID tokens and extracts user ID.
 * Sets userId as a request attribute for downstream use.
 */
@Slf4j
@Component
public class FirebaseAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";
  private static final List<String> ALLOWED_ORIGINS = List.of(
      "http://localhost:5173",
      "http://localhost:5174",
      "https://recipe-mgmt-dev.web.app",
      "https://recipe-mgmt-dev.firebaseapp.com");

  @Value("${auth.enabled}")
  private boolean authEnabled;

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain) throws ServletException, IOException {

    // Generate unique request ID for distributed tracing
    String requestId = UUID.randomUUID().toString();
    MDC.put("request.id", requestId);

    try {
      // Set HSTS header on all responses (including errors)
      response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");

      // Skip auth for OPTIONS requests (CORS preflight)
      if ("OPTIONS".equals(request.getMethod())) {
        // Set CORS headers for preflight response
        String origin = request.getHeader("Origin");
        if (origin != null && ALLOWED_ORIGINS.contains(origin)) {
          response.setHeader("Access-Control-Allow-Origin", origin);
          response.setHeader("Access-Control-Allow-Methods",
              "GET, POST, PUT, PATCH, DELETE, OPTIONS");
          response.setHeader("Access-Control-Allow-Headers", "*");
          response.setHeader("Access-Control-Allow-Credentials", "true");
          response.setHeader("Access-Control-Max-Age", "3600");
        }
        response.setStatus(HttpServletResponse.SC_OK);
        return;
      }

      String path = request.getRequestURI();

      // Skip auth for health check
      if (path.startsWith("/actuator/health")) {
        filterChain.doFilter(request, response);
        return;
      }

      // Skip auth for public recipes
      if (path.equals("/api/recipes/public")) {
        filterChain.doFilter(request, response);
        return;
      }

      // Skip auth for single public recipe endpoint (read-only methods only)
      String method = request.getMethod();
      if (path.matches("/api/recipes/[^/]+/public")
          && ("GET".equals(method) || "HEAD".equals(method))) {
        filterChain.doFilter(request, response);
        return;
      }

      // Skip auth for public user profile endpoint (read-only methods only).
      // Optionally extract the authenticated user ID so the service can populate
      // isFollowedByCurrentUser when a valid token is supplied.
      if (path.matches("/api/users/[^/]+/profile")
          && ("GET".equals(method) || "HEAD".equals(method))) {
        trySetOptionalUserId(request);
        filterChain.doFilter(request, response);
        return;
      }

      // Skip auth for public followers/following list endpoints (read-only methods only)
      if (path.matches("/api/users/[^/]+/followers")
          && ("GET".equals(method) || "HEAD".equals(method))) {
        trySetOptionalUserId(request);
        filterChain.doFilter(request, response);
        return;
      }
      if (path.matches("/api/users/[^/]+/following")
          && ("GET".equals(method) || "HEAD".equals(method))) {
        trySetOptionalUserId(request);
        filterChain.doFilter(request, response);
        return;
      }

      // Skip auth for Swagger UI and API docs
      if (path.startsWith("/v3/api-docs")
          || path.startsWith("/swagger-ui")) {
        filterChain.doFilter(request, response);
        return;
      }

      if (!authEnabled) {
        // When auth is disabled (dev/test mode only), allow caller to specify userId via header.
        // Falls back to "test-user" if no header is provided.
        String userId = request.getHeader("userId");
        if (userId == null || userId.isEmpty()) {
          userId = "test-user";
        }
        log.warn("Authentication is disabled - using user: {}", userId);
        request.setAttribute("userId", userId);
        MDC.put("user.id", userId);
        filterChain.doFilter(request, response);
        return;
      }

      String authHeader = request.getHeader("Authorization");

      if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
        log.error("Missing or invalid Authorization header");
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
            "Missing or invalid Authorization header");
        return;
      }

      String idToken = authHeader.substring(BEARER_PREFIX.length());

      try {
        FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
        String uid = decodedToken.getUid();
        String email = decodedToken.getEmail();

        log.info("Authenticated user: uid={}, email={}", uid, email);

        // Set userId as request attribute for controller access
        request.setAttribute("userId", uid);
        
        // Populate MDC with user ID for distributed tracing
        MDC.put("user.id", uid);

        filterChain.doFilter(request, response);
      } catch (FirebaseAuthException e) {
        log.error("Failed to verify Firebase token: {}", e.getMessage());
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
            "Invalid Firebase ID token");
      }
    } finally {
      // Clean up MDC to prevent memory leaks
      MDC.remove("user.id");
      MDC.remove("request.id");
    }
  }

  /**
   * Attempts to extract and set the authenticated user ID for a public endpoint that supports
   * optional authentication. Never rejects the request: if no token is provided or the token is
   * invalid the request simply proceeds without a {@code userId} attribute.
   *
   * <p>When {@code auth.enabled=false} the user ID is read from the {@code userId} request header
   * (dev/test shortcut). When {@code auth.enabled=true} a Bearer token is validated via Firebase.
   */
  private void trySetOptionalUserId(HttpServletRequest request) {
    if (!authEnabled) {
      String userId = request.getHeader("userId");
      if (userId != null && !userId.isEmpty()) {
        request.setAttribute("userId", userId);
        MDC.put("user.id", userId);
      }
      return;
    }

    String authHeader = request.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
      return;
    }

    try {
      String idToken = authHeader.substring(BEARER_PREFIX.length());
      FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
      String uid = decodedToken.getUid();
      request.setAttribute("userId", uid);
      MDC.put("user.id", uid);
    } catch (FirebaseAuthException e) {
      log.debug("Optional auth token validation failed for public profile endpoint: {}",
          e.getMessage());
    } catch (RuntimeException e) {
      log.warn("Optional auth could not be evaluated for public profile endpoint; proceeding "
          + "without userId", e);
    }
  }
}
