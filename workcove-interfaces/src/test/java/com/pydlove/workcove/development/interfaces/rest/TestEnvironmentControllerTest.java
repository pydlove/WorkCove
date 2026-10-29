package com.pydlove.workcove.development.interfaces.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pydlove.workcove.development.application.TestEnvironmentService;
import com.pydlove.workcove.development.application.TestEnvironmentService.TestEnvironmentCredentials;
import com.pydlove.workcove.development.application.TestEnvironmentService.TestEnvironmentView;
import com.pydlove.workcove.identity.application.command.AuthService;
import com.pydlove.workcove.identity.application.query.AuthenticatedUser;
import com.pydlove.workcove.identity.application.query.CurrentAuthSession;
import com.pydlove.workcove.identity.interfaces.security.SessionCookieReader;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestEnvironmentControllerTest {

    @Test
    void listViewSerializationNeverContainsPasswordFields() throws Exception {
        TestEnvironmentView view = new TestEnvironmentView(
                1L,
                "workspace-a",
                "研发环境",
                "https://dev.example.com",
                "alice",
                true,
                Instant.parse("2026-10-29T02:00:00Z"),
                Instant.parse("2026-10-29T02:00:00Z"));

        String json = new ObjectMapper()
                .findAndRegisterModules()
                .writeValueAsString(view);

        assertTrue(json.contains("\"hasPassword\":true"));
        assertFalse(json.contains("\"password\""));
        assertFalse(json.contains("passwordCiphertext"));
    }

    @Test
    void credentialsResponseIsNotCacheable() {
        TestEnvironmentService service =
                mock(TestEnvironmentService.class);
        AuthService authService = mock(AuthService.class);
        SessionCookieReader cookieReader =
                mock(SessionCookieReader.class);
        HttpServletRequest request =
                mock(HttpServletRequest.class);
        when(cookieReader.readToken(request)).thenReturn("session-token");
        when(authService.currentSession("session-token"))
                .thenReturn(new CurrentAuthSession(
                        new AuthenticatedUser(
                                7L,
                                "alice@example.com",
                                "Alice"),
                        Instant.parse("2026-10-29T10:00:00Z"),
                        false));
        when(service.credentials(7L, "workspace-a", 1L))
                .thenReturn(new TestEnvironmentCredentials(
                        "alice",
                        "secret"));
        TestEnvironmentController controller =
                new TestEnvironmentController(
                        service,
                        authService,
                        cookieReader);

        ResponseEntity<TestEnvironmentCredentials> response =
                controller.credentials(
                        "manage-test-environments",
                        "workspace-a",
                        1L,
                        request);

        assertEquals(
                "no-store",
                response.getHeaders()
                        .getCacheControl());
        assertEquals(
                "no-cache",
                response.getHeaders().getFirst(HttpHeaders.PRAGMA));
        assertEquals(
                "secret",
                response.getBody().password());
    }
}
