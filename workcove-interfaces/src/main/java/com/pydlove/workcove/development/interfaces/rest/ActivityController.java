package com.pydlove.workcove.development.interfaces.rest;

import com.pydlove.workcove.development.application.SogouInputStatsService;
import com.pydlove.workcove.development.interfaces.dto.SogouInputStatsResponse;
import com.pydlove.workcove.identity.application.command.AuthService;
import com.pydlove.workcove.identity.interfaces.security.SessionCookieReader;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/development/activity")
public class ActivityController {

    private final SogouInputStatsService sogouInputStatsService;
    private final AuthService authService;
    private final SessionCookieReader sessionCookieReader;

    public ActivityController(
            SogouInputStatsService sogouInputStatsService,
            AuthService authService,
            SessionCookieReader sessionCookieReader) {
        this.sogouInputStatsService = sogouInputStatsService;
        this.authService = authService;
        this.sessionCookieReader = sessionCookieReader;
    }

    @GetMapping("/sogou-input")
    public SogouInputStatsResponse sogouInput(
            HttpServletRequest request) {
        authService.currentSession(
                sessionCookieReader.readToken(request));
        return SogouInputStatsResponse.from(
                sogouInputStatsService.read());
    }
}
