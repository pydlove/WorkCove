package com.pydlove.workcove.development.interfaces.rest;

import com.pydlove.workcove.development.application.ZentaoService;
import com.pydlove.workcove.development.application.ZentaoTestEvidenceService;
import com.pydlove.workcove.development.application.command.ZentaoBuildCreateCommand;
import com.pydlove.workcove.development.application.command.ZentaoConnectionCommand;
import com.pydlove.workcove.development.application.command.ZentaoResolveCommand;
import com.pydlove.workcove.development.application.command.ZentaoTestAttachment;
import com.pydlove.workcove.development.application.command.ZentaoTestTaskCreateCommand;
import com.pydlove.workcove.development.application.query.ZentaoBugView;
import com.pydlove.workcove.development.application.query.ZentaoBuildOptionsView;
import com.pydlove.workcove.development.application.query.ZentaoBuildView;
import com.pydlove.workcove.development.application.query.ZentaoConnectionView;
import com.pydlove.workcove.development.application.query.ZentaoCreatedBuildView;
import com.pydlove.workcove.development.application.query.ZentaoCreatedTestTaskView;
import com.pydlove.workcove.development.application.query.ZentaoMediaView;
import com.pydlove.workcove.development.application.query.ZentaoProjectView;
import com.pydlove.workcove.development.application.query.ZentaoSyncResult;
import com.pydlove.workcove.development.application.query.ZentaoSystemView;
import com.pydlove.workcove.development.application.query.ZentaoTestEvidenceMediaView;
import com.pydlove.workcove.development.application.query.ZentaoTestEvidenceView;
import com.pydlove.workcove.development.application.query.ZentaoTestRunView;
import com.pydlove.workcove.development.application.query.ZentaoTestTaskOptionsView;
import com.pydlove.workcove.identity.application.command.AuthService;
import com.pydlove.workcove.identity.interfaces.security.SessionCookieReader;
import com.pydlove.workcove.shared.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
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
import java.util.Map;

@RestController
@RequestMapping("/api/development/zentao")
public class ZentaoController {

    private static final String ACTION = "manage-zentao";

    private final ZentaoService zentaoService;
    private final ZentaoTestEvidenceService testEvidenceService;
    private final AuthService authService;
    private final SessionCookieReader sessionCookieReader;

    public ZentaoController(
            ZentaoService zentaoService,
            ZentaoTestEvidenceService testEvidenceService,
            AuthService authService,
            SessionCookieReader sessionCookieReader) {
        this.zentaoService = zentaoService;
        this.testEvidenceService = testEvidenceService;
        this.authService = authService;
        this.sessionCookieReader = sessionCookieReader;
    }

    @GetMapping("/connection")
    public ZentaoConnectionView connection(
            @RequestParam String workspaceId,
            HttpServletRequest request) {
        return zentaoService.connection(
                currentUserId(request),
                workspaceId);
    }

    @PutMapping("/connection")
    public ZentaoConnectionView saveConnection(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @RequestBody ConnectionRequest body,
            HttpServletRequest request) {
        validateAction(action);
        return zentaoService.saveConnection(
                currentUserId(request),
                body.workspaceId(),
                new ZentaoConnectionCommand(
                        body.baseUrl(),
                        body.username(),
                        body.password(),
                        body.projectIds()));
    }

    @PostMapping("/projects")
    public List<ZentaoProjectView> projects(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @RequestBody ConnectionRequest body,
            HttpServletRequest request) {
        validateAction(action);
        return zentaoService.availableProjects(
                currentUserId(request),
                body.workspaceId(),
                new ZentaoConnectionCommand(
                        body.baseUrl(),
                        body.username(),
                        body.password(),
                        body.projectIds()));
    }

    @PostMapping("/sync")
    public ZentaoSyncResult sync(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @RequestBody WorkspaceRequest body,
            HttpServletRequest request) {
        validateAction(action);
        return zentaoService.sync(
                currentUserId(request),
                body.workspaceId());
    }

    @GetMapping("/bugs")
    public List<ZentaoBugView> bugs(
            @RequestParam String workspaceId,
            HttpServletRequest request) {
        return zentaoService.bugs(
                currentUserId(request),
                workspaceId);
    }

    @GetMapping("/builds")
    public List<ZentaoBuildView> builds(
            @RequestParam String workspaceId,
            HttpServletRequest request) {
        return zentaoService.builds(
                currentUserId(request),
                workspaceId);
    }

    @GetMapping("/build-options")
    public ZentaoBuildOptionsView buildOptions(
            @RequestParam String workspaceId,
            HttpServletRequest request) {
        return zentaoService.buildOptions(
                currentUserId(request),
                workspaceId);
    }

    @GetMapping("/systems")
    public List<ZentaoSystemView> systems(
            @RequestParam String workspaceId,
            @RequestParam String productId,
            HttpServletRequest request) {
        return zentaoService.systems(
                currentUserId(request),
                workspaceId,
                productId);
    }

    @GetMapping("/test-task-options")
    public ZentaoTestTaskOptionsView testTaskOptions(
            @RequestParam String workspaceId,
            @RequestParam String productId,
            HttpServletRequest request) {
        return zentaoService.testTaskOptions(
                currentUserId(request),
                workspaceId,
                productId);
    }

    @PostMapping("/builds")
    public ZentaoCreatedBuildView createBuild(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @RequestBody CreateBuildRequest body,
            HttpServletRequest request) {
        validateAction(action);
        return zentaoService.createBuild(
                currentUserId(request),
                body.workspaceId(),
                new ZentaoBuildCreateCommand(
                        body.projectId(),
                        body.executionId(),
                        body.productId(),
                        body.systemId(),
                        body.name(),
                        body.builder(),
                        body.date(),
                        body.downloadUrl()));
    }

    @PostMapping("/builds/{id}/test-tasks")
    public ZentaoCreatedTestTaskView createTestTask(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @PathVariable long id,
            @RequestBody CreateTestTaskRequest body,
            HttpServletRequest request) {
        validateAction(action);
        return zentaoService.createTestTask(
                currentUserId(request),
                body.workspaceId(),
                id,
                new ZentaoTestTaskCreateCommand(
                        body.types(),
                        body.owner(),
                        body.members(),
                        body.status(),
                        body.begin(),
                        body.end(),
                        body.testReport(),
                        body.name()));
    }

    @GetMapping("/bugs/{id}/media")
    public ResponseEntity<byte[]> bugMedia(
            @PathVariable long id,
            @RequestParam String workspaceId,
            @RequestParam String source,
            HttpServletRequest request) {
        ZentaoMediaView media = zentaoService.media(
                currentUserId(request),
                workspaceId,
                id,
                source);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(media.contentType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(media.content());
    }

    @PostMapping("/bugs/{id}/test-runs")
    public ZentaoTestRunView prepareTestRun(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @PathVariable long id,
            @RequestBody WorkspaceRequest body,
            HttpServletRequest request) {
        validateAction(action);
        return testEvidenceService.prepare(
                currentUserId(request),
                body.workspaceId(),
                id);
    }

    @PostMapping("/bugs/{id}/test-comment")
    public Map<String, Boolean> commentTestResult(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @PathVariable long id,
            @RequestBody TestCommentRequest body,
            HttpServletRequest request) {
        validateAction(action);
        long ownerId = currentUserId(request);
        List<ZentaoTestAttachment> screenshots =
                body.screenshots() == null
                        ? List.of()
                        : body.screenshots().stream()
                                .filter(filename -> filename != null
                                        && !filename.isBlank())
                                .distinct()
                                .map(filename -> {
                                    ZentaoTestEvidenceMediaView evidence =
                                            testEvidenceService.read(
                                                    ownerId,
                                                    body.workspaceId(),
                                                    id,
                                                    body.runId(),
                                                    filename);
                                    return new ZentaoTestAttachment(
                                            evidence.filename(),
                                            evidence.contentType(),
                                            evidence.content());
                                })
                                .toList();
        return Map.of(
                "alreadyExists",
                zentaoService.commentTestResult(
                        ownerId,
                        body.workspaceId(),
                        id,
                        body.runId(),
                        body.environment(),
                        body.proof(),
                        screenshots));
    }

    @GetMapping("/bugs/{id}/test-runs/{runId}/evidence")
    public List<ZentaoTestEvidenceView> testEvidence(
            @PathVariable long id,
            @PathVariable String runId,
            @RequestParam String workspaceId,
            HttpServletRequest request) {
        return testEvidenceService.evidence(
                currentUserId(request),
                workspaceId,
                id,
                runId);
    }

    @GetMapping("/bugs/{id}/test-runs/{runId}/evidence/{filename}")
    public ResponseEntity<byte[]> testEvidenceFile(
            @PathVariable long id,
            @PathVariable String runId,
            @PathVariable String filename,
            @RequestParam String workspaceId,
            HttpServletRequest request) {
        ZentaoTestEvidenceMediaView media =
                testEvidenceService.read(
                        currentUserId(request),
                        workspaceId,
                        id,
                        runId,
                        filename);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(media.contentType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(media.content());
    }

    @PostMapping("/bugs/{id}/resolve")
    public ZentaoBugView resolve(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @PathVariable long id,
            @RequestBody ResolveRequest body,
            HttpServletRequest request) {
        validateAction(action);
        return zentaoService.resolve(
                currentUserId(request),
                body.workspaceId(),
                id,
                new ZentaoResolveCommand(
                        body.resolution(),
                        body.solution(),
                        body.reason()));
    }

    @PutMapping("/bugs/{id}/confirm")
    public ZentaoBugView confirm(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @PathVariable long id,
            @RequestBody WorkspaceRequest body,
            HttpServletRequest request) {
        validateAction(action);
        return zentaoService.confirm(
                currentUserId(request),
                body.workspaceId(),
                id);
    }

    @PutMapping("/bugs/{id}/local-resolve")
    public ZentaoBugView resolveLocally(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @PathVariable long id,
            @RequestBody ResolveLocalRequest body,
            HttpServletRequest request) {
        validateAction(action);
        return zentaoService.resolveLocally(
                currentUserId(request),
                body.workspaceId(),
                id,
                body.solution(),
                body.reason());
    }

    @DeleteMapping("/bugs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBug(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @PathVariable long id,
            @RequestParam String workspaceId,
            HttpServletRequest request) {
        validateAction(action);
        zentaoService.deleteBug(
                currentUserId(request),
                workspaceId,
                id);
    }

    private long currentUserId(HttpServletRequest request) {
        return authService.currentSession(
                sessionCookieReader.readToken(request)).user().id();
    }

    private void validateAction(String action) {
        if (!ACTION.equals(action)) {
            throw new DomainException("不支持的禅道操作");
        }
    }

    public record ConnectionRequest(
            String workspaceId,
            String baseUrl,
            String username,
            String password,
            List<String> projectIds
    ) {
    }

    public record WorkspaceRequest(String workspaceId) {
    }

    public record TestCommentRequest(
            String workspaceId,
            String runId,
            String environment,
            String proof,
            List<String> screenshots
    ) {
    }

    public record ResolveRequest(
            String workspaceId,
            String resolution,
            String solution,
            String reason
    ) {
    }

    public record ResolveLocalRequest(
            String workspaceId,
            String solution,
            String reason
    ) {
    }

    public record CreateBuildRequest(
            String workspaceId,
            String projectId,
            String executionId,
            String productId,
            String systemId,
            String name,
            String builder,
            String date,
            String downloadUrl
    ) {
    }

    public record CreateTestTaskRequest(
            String workspaceId,
            List<String> types,
            String owner,
            List<String> members,
            String status,
            String begin,
            String end,
            String testReport,
            String name
    ) {
    }
}
