package com.pydlove.workcove.development.interfaces.rest;

import com.pydlove.workcove.development.application.TestEnvironmentService;
import com.pydlove.workcove.development.application.TestEnvironmentService.TestEnvironmentCommand;
import com.pydlove.workcove.development.application.TestEnvironmentService.TestEnvironmentCredentials;
import com.pydlove.workcove.development.application.TestEnvironmentService.TestEnvironmentView;
import com.pydlove.workcove.identity.application.command.AuthService;
import com.pydlove.workcove.identity.interfaces.security.SessionCookieReader;
import com.pydlove.workcove.shared.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/development/test-environments")
public class TestEnvironmentController {

    private static final String ACTION = "manage-test-environments";

    private final TestEnvironmentService service;
    private final AuthService authService;
    private final SessionCookieReader sessionCookieReader;

    public TestEnvironmentController(
            TestEnvironmentService service,
            AuthService authService,
            SessionCookieReader sessionCookieReader) {
        this.service = service;
        this.authService = authService;
        this.sessionCookieReader = sessionCookieReader;
    }

    @GetMapping
    public List<TestEnvironmentView> list(
            @RequestParam String workspaceId,
            HttpServletRequest request) {
        return service.list(currentUserId(request), workspaceId);
    }

    @PostMapping
    public TestEnvironmentView create(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @RequestBody TestEnvironmentRequest body,
            HttpServletRequest request) {
        validateAction(action);
        return service.save(
                currentUserId(request),
                body.workspaceId(),
                null,
                body.command());
    }

    @PutMapping("/{id}")
    public TestEnvironmentView update(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @PathVariable long id,
            @RequestBody TestEnvironmentRequest body,
            HttpServletRequest request) {
        validateAction(action);
        return service.save(
                currentUserId(request),
                body.workspaceId(),
                id,
                body.command());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @RequestParam String workspaceId,
            @PathVariable long id,
            HttpServletRequest request) {
        validateAction(action);
        service.delete(
                currentUserId(request),
                workspaceId,
                id);
    }

    @PostMapping("/{id}/credentials")
    public ResponseEntity<TestEnvironmentCredentials> credentials(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @RequestParam String workspaceId,
            @PathVariable long id,
            HttpServletRequest request) {
        validateAction(action);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .body(service.credentials(
                        currentUserId(request),
                        workspaceId,
                        id));
    }

    private long currentUserId(HttpServletRequest request) {
        return authService.currentSession(
                sessionCookieReader.readToken(request)).user().id();
    }

    private void validateAction(String action) {
        if (!ACTION.equals(action)) {
            throw new DomainException("不支持的测试环境操作");
        }
    }

    public record TestEnvironmentRequest(
            String workspaceId,
            String name,
            String url,
            String username,
            String password
    ) {
        TestEnvironmentCommand command() {
            return new TestEnvironmentCommand(
                    name,
                    url,
                    username,
                    password);
        }
    }
}
