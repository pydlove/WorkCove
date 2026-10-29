package com.pydlove.workcove.development.interfaces.rest;

import com.pydlove.workcove.development.application.WorkFileSearchService;
import com.pydlove.workcove.development.domain.model.WorkFileMatch;
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

@RestController
@RequestMapping("/api/development/work-files")
public class DevelopmentWorkFileController {

    private static final String SEARCH_ACTION = "search-work-files";
    private static final String OPEN_ACTION = "open-work-file";

    private final WorkFileSearchService workFileSearchService;
    private final AuthService authService;
    private final SessionCookieReader sessionCookieReader;

    public DevelopmentWorkFileController(
            WorkFileSearchService workFileSearchService,
            AuthService authService,
            SessionCookieReader sessionCookieReader) {
        this.workFileSearchService = workFileSearchService;
        this.authService = authService;
        this.sessionCookieReader = sessionCookieReader;
    }

    @PostMapping("/search")
    public List<WorkFileMatchResponse> search(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @RequestBody WorkFileSearchRequest payload,
            HttpServletRequest request) {
        authorize(action, SEARCH_ACTION, request);
        return workFileSearchService
                .search(payload.roots(), payload.query())
                .stream()
                .map(WorkFileMatchResponse::from)
                .toList();
    }

    @PostMapping("/open")
    public void open(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @RequestBody WorkFileOpenRequest payload,
            HttpServletRequest request) {
        authorize(action, OPEN_ACTION, request);
        workFileSearchService.openFile(payload.path(), payload.roots());
    }

    private void authorize(
            String action,
            String expectedAction,
            HttpServletRequest request) {
        authService.currentSession(sessionCookieReader.readToken(request));
        if (!expectedAction.equals(action)) {
            throw new DomainException("不支持的工作目录文件操作");
        }
    }

    public record WorkFileSearchRequest(
            List<String> roots,
            String query) {
    }

    public record WorkFileOpenRequest(
            String path,
            List<String> roots) {
    }

    public record WorkFileMatchResponse(
            String name,
            String path,
            String rootName,
            String relativePath) {

        public static WorkFileMatchResponse from(WorkFileMatch match) {
            return new WorkFileMatchResponse(
                    match.name(),
                    match.path(),
                    match.rootName(),
                    match.relativePath());
        }
    }
}
