package com.pydlove.workcove.development.interfaces.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.pydlove.workcove.development.application.UserLocalStateService;
import com.pydlove.workcove.identity.application.command.AuthService;
import com.pydlove.workcove.identity.interfaces.security.SessionCookieReader;
import com.pydlove.workcove.shared.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/development/user-state")
public class UserLocalStateController {

    private static final String ACTION = "manage-user-state";

    private final UserLocalStateService stateService;
    private final AuthService authService;
    private final SessionCookieReader sessionCookieReader;

    public UserLocalStateController(
            UserLocalStateService stateService,
            AuthService authService,
            SessionCookieReader sessionCookieReader) {
        this.stateService = stateService;
        this.authService = authService;
        this.sessionCookieReader = sessionCookieReader;
    }

    @GetMapping
    public Map<String, String> all(HttpServletRequest request) {
        return stateService.all(currentUserId(request));
    }

    @PutMapping("/{stateKey}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void save(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @PathVariable String stateKey,
            @RequestBody JsonNode payload,
            HttpServletRequest request) {
        if (!ACTION.equals(action)) {
            throw new DomainException("不支持的用户状态操作");
        }
        stateService.save(
                currentUserId(request),
                stateKey,
                payload.toString());
    }

    private long currentUserId(HttpServletRequest request) {
        return authService.currentSession(
                sessionCookieReader.readToken(request)).user().id();
    }
}
