package com.pydlove.workcove.development.interfaces.rest;

import com.pydlove.workcove.development.domain.port.ApplicationHost;
import com.pydlove.workcove.identity.application.command.AuthService;
import com.pydlove.workcove.identity.interfaces.security.SessionCookieReader;
import com.pydlove.workcove.shared.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/host/applications")
public class HostApplicationController {

    private static final String ACTION_HEADER = "X-WorkCove-Action";

    private final ApplicationHost applicationHost;
    private final AuthService authService;
    private final SessionCookieReader sessionCookieReader;

    public HostApplicationController(
            ApplicationHost applicationHost,
            AuthService authService,
            SessionCookieReader sessionCookieReader) {
        this.applicationHost = applicationHost;
        this.authService = authService;
        this.sessionCookieReader = sessionCookieReader;
    }

    @PostMapping("/picker")
    public SelectedApplicationResponse chooseApplication(
            @RequestHeader(value = ACTION_HEADER, required = false)
            String action,
            HttpServletRequest request) {
        requireAction(action, "choose-application");
        requireSession(request);
        ApplicationHost.SelectedApplication application =
                applicationHost.chooseApplication();
        if (application == null) return null;
        return new SelectedApplicationResponse(
                application.path(),
                application.name(),
                application.iconDataUrl());
    }

    @PostMapping("/open")
    public void openApplication(
            @RequestHeader(value = ACTION_HEADER, required = false)
            String action,
            @RequestBody ApplicationPathRequest body,
            HttpServletRequest request) {
        requireAction(action, "open-application");
        requireSession(request);
        applicationHost.openApplication(body.path());
    }

    @PostMapping("/running")
    public List<ApplicationRunningResponse> runningApplications(
            @RequestHeader(value = ACTION_HEADER, required = false)
            String action,
            @RequestBody ApplicationPathsRequest body,
            HttpServletRequest request) {
        requireAction(action, "application-running");
        requireSession(request);
        List<String> paths = body == null || body.paths() == null
                ? List.of()
                : body.paths().stream()
                        .filter((path) -> path != null && !path.isBlank())
                        .distinct()
                        .toList();
        if (paths.size() > 200) {
            throw new DomainException("一次最多检测 200 个应用");
        }
        if (paths.stream().anyMatch((path) -> path.length() > 1024)) {
            throw new DomainException("应用路径过长");
        }
        if (paths.isEmpty()) return List.of();
        Set<String> running = applicationHost.runningApplicationPaths(paths);
        return paths.stream()
                .map((path) -> new ApplicationRunningResponse(
                        path,
                        running.contains(path)))
                .toList();
    }

    @PostMapping("/force-quit")
    public void forceQuitApplication(
            @RequestHeader(value = ACTION_HEADER, required = false)
            String action,
            @RequestBody ApplicationPathRequest body,
            HttpServletRequest request) {
        requireAction(action, "force-quit-application");
        requireSession(request);
        applicationHost.forceQuitApplication(body.path());
    }

    private void requireSession(HttpServletRequest request) {
        authService.currentSession(
                sessionCookieReader.readToken(request));
    }

    private void requireAction(String action, String expected) {
        if (!expected.equals(action)) {
            throw new DomainException("本机操作请求无效");
        }
    }

    public record ApplicationPathRequest(String path) {
    }

    public record ApplicationPathsRequest(List<String> paths) {
    }

    public record ApplicationRunningResponse(
            String path,
            boolean running) {
    }

    public record SelectedApplicationResponse(
            String path,
            String name,
            String iconDataUrl
    ) {
    }
}
