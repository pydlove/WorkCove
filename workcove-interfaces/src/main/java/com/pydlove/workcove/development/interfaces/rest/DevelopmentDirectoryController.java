package com.pydlove.workcove.development.interfaces.rest;

import com.pydlove.workcove.development.domain.model.LocalDirectorySummary;
import com.pydlove.workcove.development.domain.port.DirectoryPicker;
import com.pydlove.workcove.development.domain.port.IdeLauncher;
import com.pydlove.workcove.development.domain.port.LocalDirectoryInspector;
import com.pydlove.workcove.identity.application.command.AuthService;
import com.pydlove.workcove.identity.interfaces.security.SessionCookieReader;
import com.pydlove.workcove.shared.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/development")
public class DevelopmentDirectoryController {

    private static final String CHOOSE_DIRECTORY_ACTION = "choose-directory";
    private static final String OPEN_DIRECTORY_ACTION = "open-directory";
    private static final String OPEN_WITH_IDEA_ACTION = "open-with-idea";
    private static final String INSPECT_DIRECTORY_ACTION =
            "inspect-directory";

    private final AuthService authService;
    private final SessionCookieReader sessionCookieReader;
    private final DirectoryPicker directoryPicker;
    private final IdeLauncher ideLauncher;
    private final LocalDirectoryInspector localDirectoryInspector;

    public DevelopmentDirectoryController(
            AuthService authService,
            SessionCookieReader sessionCookieReader,
            DirectoryPicker directoryPicker,
            IdeLauncher ideLauncher,
            LocalDirectoryInspector localDirectoryInspector) {
        this.authService = authService;
        this.sessionCookieReader = sessionCookieReader;
        this.directoryPicker = directoryPicker;
        this.ideLauncher = ideLauncher;
        this.localDirectoryInspector = localDirectoryInspector;
    }

    @PostMapping("/directory-picker")
    public DirectoryPickerResponse chooseDirectory(
            @RequestHeader(value = "X-WorkCove-Action", required = false) String action,
            HttpServletRequest request) {
        authService.currentSession(sessionCookieReader.readToken(request));
        if (!CHOOSE_DIRECTORY_ACTION.equals(action)) {
            throw new DomainException("不支持的本地目录操作");
        }
        return new DirectoryPickerResponse(directoryPicker.chooseDirectory());
    }

    @PostMapping("/open-directory")
    public void openDirectory(
            @RequestHeader(value = "X-WorkCove-Action", required = false) String action,
            @RequestBody DirectoryPathRequest body,
            HttpServletRequest request) {
        authService.currentSession(sessionCookieReader.readToken(request));
        if (!OPEN_DIRECTORY_ACTION.equals(action)) {
            throw new DomainException("不支持的本地目录操作");
        }
        directoryPicker.openDirectory(body.path());
    }

    @PostMapping("/open-with-idea")
    public void openWithIdea(
            @RequestHeader(value = "X-WorkCove-Action", required = false) String action,
            @RequestBody DirectoryPathRequest body,
            HttpServletRequest request) {
        authService.currentSession(sessionCookieReader.readToken(request));
        if (!OPEN_WITH_IDEA_ACTION.equals(action)) {
            throw new DomainException("不支持的本地目录操作");
        }
        ideLauncher.openProject(body.path());
    }

    @PostMapping("/directory-summary")
    public LocalDirectorySummary directorySummary(
            @RequestHeader(value = "X-WorkCove-Action", required = false) String action,
            @RequestBody DirectoryPathRequest body,
            HttpServletRequest request) {
        authService.currentSession(sessionCookieReader.readToken(request));
        if (!INSPECT_DIRECTORY_ACTION.equals(action)) {
            throw new DomainException("不支持的本地目录操作");
        }
        long threshold = body.largeFileThresholdBytes() == null
                ? LocalDirectoryInspector.DEFAULT_LARGE_FILE_THRESHOLD_BYTES
                : body.largeFileThresholdBytes();
        return localDirectoryInspector.inspect(body.path(), threshold);
    }

    public record DirectoryPickerResponse(String path) {
    }

    public record DirectoryPathRequest(
            String path,
            Long largeFileThresholdBytes) {
    }
}
