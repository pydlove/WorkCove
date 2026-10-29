package com.pydlove.workcove.development.interfaces.rest;

import com.pydlove.workcove.development.application.ManagedServerService;
import com.pydlove.workcove.development.application.command.ManagedServerCommand;
import com.pydlove.workcove.development.domain.model.ServerTool;
import com.pydlove.workcove.development.domain.port.ServerLauncher;
import com.pydlove.workcove.development.interfaces.dto.ManagedServerRequest;
import com.pydlove.workcove.development.interfaces.dto.ManagedServerResponse;
import com.pydlove.workcove.development.interfaces.dto.OpenManagedServerRequest;
import com.pydlove.workcove.development.interfaces.dto.OpenManagedServerResponse;
import com.pydlove.workcove.identity.application.command.AuthService;
import com.pydlove.workcove.identity.interfaces.security.SessionCookieReader;
import com.pydlove.workcove.shared.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/development/servers")
public class ManagedServerController {

    private static final String ACTION = "manage-servers";

    private final ManagedServerService serverService;
    private final AuthService authService;
    private final SessionCookieReader sessionCookieReader;

    public ManagedServerController(
            ManagedServerService serverService,
            AuthService authService,
            SessionCookieReader sessionCookieReader) {
        this.serverService = serverService;
        this.authService = authService;
        this.sessionCookieReader = sessionCookieReader;
    }

    @GetMapping
    public List<ManagedServerResponse> list(
            @RequestParam String workspaceId,
            HttpServletRequest request) {
        return serverService.list(currentUserId(request), workspaceId).stream()
                .map(ManagedServerResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ManagedServerResponse create(
            @RequestHeader(value = "X-WorkCove-Action", required = false) String action,
            @RequestBody ManagedServerRequest request,
            HttpServletRequest servletRequest) {
        validateAction(action);
        return ManagedServerResponse.from(serverService.create(
                currentUserId(servletRequest),
                request.workspaceId(),
                toCommand(request)));
    }

    @PutMapping("/{id}")
    public ManagedServerResponse update(
            @RequestHeader(value = "X-WorkCove-Action", required = false) String action,
            @PathVariable long id,
            @RequestBody ManagedServerRequest request,
            HttpServletRequest servletRequest) {
        validateAction(action);
        return ManagedServerResponse.from(serverService.update(
                currentUserId(servletRequest),
                request.workspaceId(),
                id,
                toCommand(request)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @RequestHeader(value = "X-WorkCove-Action", required = false) String action,
            @PathVariable long id,
            @RequestParam String workspaceId,
            HttpServletRequest servletRequest) {
        validateAction(action);
        serverService.delete(
                currentUserId(servletRequest),
                workspaceId,
                id);
    }

    @PostMapping("/{id}/open")
    public OpenManagedServerResponse open(
            @RequestHeader(value = "X-WorkCove-Action", required = false) String action,
            @PathVariable long id,
            @RequestBody OpenManagedServerRequest request,
            @RequestParam String workspaceId,
            HttpServletRequest servletRequest) {
        validateAction(action);
        if (request.tool() == null) {
            throw new DomainException("请选择打开方式");
        }
        ServerTool tool = request.tool();
        ServerLauncher.LaunchResult result = serverService.open(
                currentUserId(servletRequest),
                workspaceId,
                id,
                tool);
        return new OpenManagedServerResponse(tool, result.passwordCopied());
    }

    @PostMapping("/{id}/copy")
    public ManagedServerResponse copyToWorkspace(
            @RequestHeader(value = "X-WorkCove-Action", required = false) String action,
            @PathVariable long id,
            @RequestBody TransferManagedServerRequest request,
            HttpServletRequest servletRequest) {
        validateAction(action);
        return ManagedServerResponse.from(serverService.copyToWorkspace(
                currentUserId(servletRequest),
                request.sourceWorkspaceId(),
                id,
                request.targetWorkspaceId()));
    }

    @PostMapping("/{id}/move")
    public ManagedServerResponse moveToWorkspace(
            @RequestHeader(value = "X-WorkCove-Action", required = false) String action,
            @PathVariable long id,
            @RequestBody TransferManagedServerRequest request,
            HttpServletRequest servletRequest) {
        validateAction(action);
        return ManagedServerResponse.from(serverService.moveToWorkspace(
                currentUserId(servletRequest),
                request.sourceWorkspaceId(),
                id,
                request.targetWorkspaceId()));
    }

    private ManagedServerCommand toCommand(ManagedServerRequest request) {
        return new ManagedServerCommand(
                request.name(),
                request.host(),
                request.port(),
                request.username(),
                request.password());
    }

    private long currentUserId(HttpServletRequest request) {
        return authService.currentSession(
                sessionCookieReader.readToken(request)).user().id();
    }

    private void validateAction(String action) {
        if (!ACTION.equals(action)) {
            throw new DomainException("不支持的服务器管理操作");
        }
    }

    public record TransferManagedServerRequest(
            String sourceWorkspaceId,
            String targetWorkspaceId) {
    }
}
