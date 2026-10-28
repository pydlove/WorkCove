package com.pydlove.workcove.development.interfaces.rest;

import com.pydlove.workcove.development.domain.port.CcSwitchUsageGateway;
import com.pydlove.workcove.identity.application.command.AuthService;
import com.pydlove.workcove.identity.interfaces.security.SessionCookieReader;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/development/cc-switch")
public class CcSwitchUsageController {

    private final CcSwitchUsageGateway usageGateway;
    private final AuthService authService;
    private final SessionCookieReader sessionCookieReader;

    public CcSwitchUsageController(
            CcSwitchUsageGateway usageGateway,
            AuthService authService,
            SessionCookieReader sessionCookieReader) {
        this.usageGateway = usageGateway;
        this.authService = authService;
        this.sessionCookieReader = sessionCookieReader;
    }

    @GetMapping("/usage")
    public CcSwitchUsageGateway.Usage usage(
            @RequestParam(required = false) Long from,
            HttpServletRequest request) {
        authService.currentSession(
                sessionCookieReader.readToken(request)).user().id();
        return usageGateway.read(from);
    }
}
