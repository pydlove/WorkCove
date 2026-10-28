package com.pydlove.workcove.development.interfaces.rest;

import com.pydlove.workcove.development.domain.port.DirectoryPicker;
import com.pydlove.workcove.development.domain.port.IdeLauncher;
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

    private final AuthService authService;
    private final SessionCookieReader sessionCookieReader;
    private final DirectoryPicker directoryPicker;
    private final IdeLauncher ideLauncher;

    public DevelopmentDirectoryController(
            AuthService authService,
            SessionCookieReader sessionCookieReader,
            DirectoryPicker directoryPicker,
            IdeLauncher ideLauncher) {
        this.authService = authService;
        this.sessionCookieReader = sessionCookieReader;
        this.directoryPicker = directoryPicker;
        this.ideLauncher = ideLauncher;
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

    public record DirectoryPickerResponse(String path) {
    }

    public record DirectoryPathRequest(String path) {
    }
}
