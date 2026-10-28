package com.pydlove.workcove.development.interfaces.rest;

import com.pydlove.workcove.development.application.GitRepositoryService;
import com.pydlove.workcove.development.interfaces.dto.GitBranchResponse;
import com.pydlove.workcove.development.interfaces.dto.GitCommitResponse;
import com.pydlove.workcove.development.interfaces.dto.GitRepositoryInspectRequest;
import com.pydlove.workcove.development.interfaces.dto.GitRepositoryLogRequest;
import com.pydlove.workcove.development.interfaces.dto.GitRepositoryOperationRequest;
import com.pydlove.workcove.development.interfaces.dto.GitRepositoryStatusResponse;
import com.pydlove.workcove.development.interfaces.dto.GitOperationResponse;
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
@RequestMapping("/api/development/git")
public class GitRepositoryController {

    private static final String ACTION = "manage-git-repositories";

    private final GitRepositoryService gitRepositoryService;
    private final AuthService authService;
    private final SessionCookieReader sessionCookieReader;

    public GitRepositoryController(
            GitRepositoryService gitRepositoryService,
            AuthService authService,
            SessionCookieReader sessionCookieReader) {
        this.gitRepositoryService = gitRepositoryService;
        this.authService = authService;
        this.sessionCookieReader = sessionCookieReader;
    }

    @PostMapping("/inspect")
    public GitRepositoryStatusResponse inspect(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @RequestBody GitRepositoryInspectRequest payload,
            HttpServletRequest request) {
        authService.currentSession(sessionCookieReader.readToken(request));
        if (!ACTION.equals(action)) {
            throw new DomainException("不支持的 Git 仓库操作");
        }
        return GitRepositoryStatusResponse.from(
                gitRepositoryService.inspect(payload.path()));
    }

    @PostMapping("/branches")
    public List<GitBranchResponse> branches(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @RequestBody GitRepositoryInspectRequest payload,
            HttpServletRequest request) {
        authorize(action, request);
        return gitRepositoryService.branches(payload.path()).stream()
                .map(GitBranchResponse::from)
                .toList();
    }

    @PostMapping("/log")
    public List<GitCommitResponse> log(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @RequestBody GitRepositoryLogRequest payload,
            HttpServletRequest request) {
        authorize(action, request);
        int limit = payload.limit() == null ? 50 : payload.limit();
        return gitRepositoryService.log(payload.path(), limit).stream()
                .map(GitCommitResponse::from)
                .toList();
    }

    @PostMapping("/execute")
    public GitOperationResponse execute(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @RequestBody GitRepositoryOperationRequest payload,
            HttpServletRequest request) {
        authorize(action, request);
        return GitOperationResponse.from(
                gitRepositoryService.execute(
                        payload.path(),
                        payload.toCommand()));
    }

    private void authorize(
            String action,
            HttpServletRequest request) {
        authService.currentSession(sessionCookieReader.readToken(request));
        if (!ACTION.equals(action)) {
            throw new DomainException("不支持的 Git 仓库操作");
        }
    }
}
