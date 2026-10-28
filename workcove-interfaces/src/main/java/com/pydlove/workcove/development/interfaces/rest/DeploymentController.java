package com.pydlove.workcove.development.interfaces.rest;

import com.pydlove.workcove.development.application.DeploymentExecutionService;
import com.pydlove.workcove.development.application.command.DeploymentExecutionCommand;
import com.pydlove.workcove.development.interfaces.dto.DeploymentExecutionRequest;
import com.pydlove.workcove.development.interfaces.dto.DeploymentExecutionResponse;
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
@RequestMapping("/api/development/deployments")
public class DeploymentController {

    private static final String ACTION = "deploy-script";

    private final DeploymentExecutionService executionService;
    private final AuthService authService;
    private final SessionCookieReader sessionCookieReader;

    public DeploymentController(
            DeploymentExecutionService executionService,
            AuthService authService,
            SessionCookieReader sessionCookieReader) {
        this.executionService = executionService;
        this.authService = authService;
        this.sessionCookieReader = sessionCookieReader;
    }

    @PostMapping("/execute")
    public DeploymentExecutionResponse execute(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @RequestBody DeploymentExecutionRequest request,
            HttpServletRequest servletRequest) {
        if (!ACTION.equals(action)) {
            throw new DomainException("不支持的部署操作");
        }
        return DeploymentExecutionResponse.from(
                executionService.execute(
                        currentUserId(servletRequest),
                        new DeploymentExecutionCommand(
                                request.workspaceId(),
                                request.serverId(),
                                request.remoteDirectory(),
                                request.scriptFilename(),
                                request.scriptContent())));
    }

    private long currentUserId(HttpServletRequest request) {
        return authService.currentSession(
                sessionCookieReader.readToken(request)).user().id();
    }
}
