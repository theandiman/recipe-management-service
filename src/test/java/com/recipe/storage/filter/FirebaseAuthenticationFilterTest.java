package com.recipe.storage.filter;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FirebaseAuthenticationFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private FirebaseAuth firebaseAuth;

    @Mock
    private FirebaseToken firebaseToken;

    private FirebaseAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new FirebaseAuthenticationFilter();
    }

    @Test
    void doFilterInternal_OptionsRequest_ReturnsOkWithCorsHeaders() throws ServletException, IOException {
        // Arrange
        when(request.getMethod()).thenReturn("OPTIONS");
        when(request.getHeader("Origin")).thenReturn("http://localhost:5173");

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(response).setHeader("Access-Control-Allow-Origin", "http://localhost:5173");
        verify(response).setHeader("Access-Control-Allow-Methods",
            "GET, POST, PUT, PATCH, DELETE, OPTIONS");
        verify(response).setHeader("Access-Control-Allow-Headers", "*");
        verify(response).setHeader("Access-Control-Allow-Credentials", "true");
        verify(response).setHeader("Access-Control-Max-Age", "3600");
        verify(response).setStatus(HttpServletResponse.SC_OK);
        verifyNoInteractions(filterChain);
    }

    @Test
    void doFilterInternal_OptionsRequest_UnknownOrigin_DoesNotSetCorsHeaders() throws ServletException, IOException {
        // Arrange
        when(request.getMethod()).thenReturn("OPTIONS");
        when(request.getHeader("Origin")).thenReturn("http://evil.com");

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(response).setStatus(HttpServletResponse.SC_OK);
        verify(response, never()).setHeader(eq("Access-Control-Allow-Origin"), anyString());
        verifyNoInteractions(filterChain);
    }

    @Test
    void doFilterInternal_HealthCheckPath_BypassesAuth() throws ServletException, IOException {
        // Arrange
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/actuator/health");

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain).doFilter(request, response);
        verify(request, never()).setAttribute(anyString(), anyString());
    }

    @Test
    void doFilterInternal_PublicRecipesPath_BypassesAuth() throws ServletException, IOException {
        // Arrange
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/recipes/public");

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain).doFilter(request, response);
        verify(request, never()).setAttribute(anyString(), anyString());
    }

    @Test
    void doFilterInternal_SinglePublicRecipePath_BypassesAuth() throws ServletException, IOException {
        // Arrange
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/recipes/abc123/public");

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain).doFilter(request, response);
        verify(request, never()).setAttribute(anyString(), anyString());
    }

    @Test
    void doFilterInternal_SinglePublicRecipePath_PostMethod_DoesNotBypassAuth() throws ServletException, IOException {
        // Arrange — POST to the public path must NOT bypass auth
        ReflectionTestUtils.setField(filter, "authEnabled", true);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/recipes/abc123/public");
        when(request.getHeader("Authorization")).thenReturn(null);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert — filter should reject the request with 401
        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED,
                "Missing or invalid Authorization header");
        verifyNoInteractions(filterChain);
    }

    @Test
    void doFilterInternal_SwaggerPath_BypassesAuth() throws ServletException, IOException {
        // Arrange
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/swagger-ui/index.html");

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain).doFilter(request, response);
        verify(request, never()).setAttribute(anyString(), anyString());
    }

    @Test
    void doFilterInternal_ApiDocsPath_BypassesAuth() throws ServletException, IOException {
        // Arrange
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/v3/api-docs");

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain).doFilter(request, response);
        verify(request, never()).setAttribute(anyString(), anyString());
    }

    @Test
    void doFilterInternal_AuthDisabled_UsesTestUser() throws ServletException, IOException {
        // Arrange
        ReflectionTestUtils.setField(filter, "authEnabled", false);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/recipes");

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(request).setAttribute("userId", "test-user");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_MissingAuthHeader_ReturnsUnauthorized() throws ServletException, IOException {
        // Arrange
        ReflectionTestUtils.setField(filter, "authEnabled", true);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/recipes");
        when(request.getHeader("Authorization")).thenReturn(null);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, 
                "Missing or invalid Authorization header");
        verifyNoInteractions(filterChain);
    }

    @Test
    void doFilterInternal_InvalidAuthHeaderFormat_ReturnsUnauthorized() throws ServletException, IOException {
        // Arrange
        ReflectionTestUtils.setField(filter, "authEnabled", true);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/recipes");
        when(request.getHeader("Authorization")).thenReturn("InvalidFormat token123");

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED,
                "Missing or invalid Authorization header");
        verifyNoInteractions(filterChain);
    }

    @Test
    void doFilterInternal_ValidToken_AuthenticatesUser() throws Exception {
        // Arrange
        ReflectionTestUtils.setField(filter, "authEnabled", true);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/recipes");
        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token-123");
        
        when(firebaseToken.getUid()).thenReturn("user-123");
        when(firebaseToken.getEmail()).thenReturn("user@example.com");

        // Act & Assert
        try (MockedStatic<FirebaseAuth> mockedFirebaseAuth = mockStatic(FirebaseAuth.class)) {
            mockedFirebaseAuth.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);
            when(firebaseAuth.verifyIdToken("valid-token-123")).thenReturn(firebaseToken);

            filter.doFilterInternal(request, response, filterChain);

            verify(request).setAttribute("userId", "user-123");
            verify(filterChain).doFilter(request, response);
        }
    }

    @Test
    void doFilterInternal_InvalidToken_ReturnsUnauthorized() throws Exception {
        // Arrange
        ReflectionTestUtils.setField(filter, "authEnabled", true);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/recipes");
        when(request.getHeader("Authorization")).thenReturn("Bearer invalid-token");

        FirebaseAuthException authException = mock(FirebaseAuthException.class);
        when(authException.getMessage()).thenReturn("Token expired");

        // Act & Assert
        try (MockedStatic<FirebaseAuth> mockedFirebaseAuth = mockStatic(FirebaseAuth.class)) {
            mockedFirebaseAuth.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);
            when(firebaseAuth.verifyIdToken("invalid-token")).thenThrow(authException);

            filter.doFilterInternal(request, response, filterChain);

            verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED,
                    "Invalid Firebase ID token");
            verifyNoInteractions(filterChain);
        }
    }

    // -------------------------------------------------------------------------
    // trySetOptionalUserId tests (exercised via the /api/users/{uid}/profile path)
    // -------------------------------------------------------------------------

    @Test
    void doFilterInternal_UserProfilePath_AuthDisabled_WithUserIdHeader_SetsUserIdAndProceeds()
            throws ServletException, IOException {
        // Arrange
        ReflectionTestUtils.setField(filter, "authEnabled", false);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/users/user123/profile");
        when(request.getHeader("userId")).thenReturn("caller456");

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(request).setAttribute("userId", "caller456");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_UserProfilePath_AuthDisabled_NoUserIdHeader_ProceedsWithoutUserId()
            throws ServletException, IOException {
        // Arrange
        ReflectionTestUtils.setField(filter, "authEnabled", false);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/users/user123/profile");
        when(request.getHeader("userId")).thenReturn(null);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert — no userId attribute set, but the request is allowed through
        verify(request, never()).setAttribute(anyString(), anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_SelfProfilePath_WithoutToken_RequiresAuthentication()
            throws ServletException, IOException {
        ReflectionTestUtils.setField(filter, "authEnabled", true);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/users/me/profile");
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED,
                "Missing or invalid Authorization header");
        verifyNoInteractions(filterChain);
    }

    @Test
    void doFilterInternal_UserProfilePath_AuthEnabled_NoAuthHeader_ProceedsWithoutUserId()
            throws ServletException, IOException {
        // Arrange
        ReflectionTestUtils.setField(filter, "authEnabled", true);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/users/user123/profile");
        when(request.getHeader("Authorization")).thenReturn(null);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert — no userId set, request allowed through (optional auth)
        verify(request, never()).setAttribute(anyString(), anyString());
        verify(filterChain).doFilter(request, response);
        verify(response, never()).sendError(anyInt(), anyString());
    }

    @Test
    void doFilterInternal_UserProfilePath_AuthEnabled_ValidToken_SetsUserIdAndProceeds()
            throws Exception {
        // Arrange
        ReflectionTestUtils.setField(filter, "authEnabled", true);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/users/user123/profile");
        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
        when(firebaseToken.getUid()).thenReturn("caller456");

        // Act & Assert
        try (MockedStatic<FirebaseAuth> mockedFirebaseAuth = mockStatic(FirebaseAuth.class)) {
            mockedFirebaseAuth.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);
            when(firebaseAuth.verifyIdToken("valid-token")).thenReturn(firebaseToken);

            filter.doFilterInternal(request, response, filterChain);

            verify(request).setAttribute("userId", "caller456");
            verify(filterChain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }
    }

    @Test
    void doFilterInternal_UserProfilePath_AuthEnabled_InvalidToken_ProceedsWithoutUserId()
            throws Exception {
        // Arrange
        ReflectionTestUtils.setField(filter, "authEnabled", true);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/users/user123/profile");
        when(request.getHeader("Authorization")).thenReturn("Bearer bad-token");

        FirebaseAuthException authException = mock(FirebaseAuthException.class);
        when(authException.getMessage()).thenReturn("Token invalid");

        // Act & Assert — optional auth: bad token is silently ignored, request proceeds
        try (MockedStatic<FirebaseAuth> mockedFirebaseAuth = mockStatic(FirebaseAuth.class)) {
            mockedFirebaseAuth.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);
            when(firebaseAuth.verifyIdToken("bad-token")).thenThrow(authException);

            filter.doFilterInternal(request, response, filterChain);

            verify(request, never()).setAttribute(anyString(), anyString());
            verify(filterChain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }
    }

    @Test
    void doFilterInternal_UserProfilePath_AuthEnabled_RuntimeException_ProceedsWithoutUserId()
            throws Exception {
        // Arrange
        ReflectionTestUtils.setField(filter, "authEnabled", true);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/users/user123/profile");
        when(request.getHeader("Authorization")).thenReturn("Bearer some-token");

        // Act & Assert — unexpected RuntimeException is silently swallowed, request proceeds
        try (MockedStatic<FirebaseAuth> mockedFirebaseAuth = mockStatic(FirebaseAuth.class)) {
            mockedFirebaseAuth.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);
            when(firebaseAuth.verifyIdToken("some-token"))
                    .thenThrow(new IllegalStateException("unexpected"));

            filter.doFilterInternal(request, response, filterChain);

            verify(request, never()).setAttribute(anyString(), anyString());
            verify(filterChain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }
    }
}
