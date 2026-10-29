package com.pydlove.workcove.development.interfaces.rest;

import com.pydlove.workcove.development.application.CodexAttachmentService;
import com.pydlove.workcove.development.domain.model.CodexAttachment;
import com.pydlove.workcove.development.domain.port.CodexGateway;
import com.pydlove.workcove.development.domain.port.DirectoryPicker;
import com.pydlove.workcove.identity.application.command.AuthService;
import com.pydlove.workcove.identity.interfaces.security.SessionCookieReader;
import com.pydlove.workcove.shared.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/api/codex")
public class CodexController {

    private static final String ATTACHMENT_ACTION =
            "upload-codex-attachment";

    private final AuthService authService;
    private final SessionCookieReader sessionCookieReader;
    private final CodexGateway codexGateway;
    private final DirectoryPicker directoryPicker;
    private final CodexAttachmentService attachmentService;

    public CodexController(
            AuthService authService,
            SessionCookieReader sessionCookieReader,
            CodexGateway codexGateway,
            DirectoryPicker directoryPicker,
            CodexAttachmentService attachmentService) {
        this.authService = authService;
        this.sessionCookieReader = sessionCookieReader;
        this.codexGateway = codexGateway;
        this.directoryPicker = directoryPicker;
        this.attachmentService = attachmentService;
    }

    @GetMapping("/status")
    public StatusResponse status(HttpServletRequest request) {
        authenticate(request);
        return new StatusResponse(
                codexGateway.isAvailable(),
                codexGateway.isConnected(),
                codexGateway.executable());
    }

    @PostMapping("/directory-picker")
    public DirectoryPickerResponse chooseDirectory(HttpServletRequest request) {
        authenticate(request);
        return new DirectoryPickerResponse(directoryPicker.chooseDirectory());
    }

    @GetMapping("/models")
    public Map<String, Object> models(HttpServletRequest request) {
        authenticate(request);
        return codexGateway.listModels();
    }

    @PostMapping(
            value = "/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AttachmentResponse uploadAttachment(
            @RequestHeader(
                    value = "X-WorkCove-Action",
                    required = false) String action,
            @RequestParam String workspaceId,
            @RequestPart("file") MultipartFile file,
            HttpServletRequest request) {
        if (!ATTACHMENT_ACTION.equals(action)) {
            throw new DomainException("不支持的 Codex 附件操作");
        }
        long ownerId = currentUserId(request);
        try {
            CodexAttachment attachment = attachmentService.upload(
                    ownerId,
                    workspaceId,
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getSize(),
                    file.getInputStream());
            return new AttachmentResponse(
                    attachment.id(),
                    attachment.name(),
                    attachment.contentType(),
                    attachment.size(),
                    attachment.image() ? "image" : "file");
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取上传附件", exception);
        }
    }

    @PostMapping("/threads")
    public Map<String, Object> startThread(
            @RequestBody StartThreadRequest body,
            HttpServletRequest request) {
        authenticate(request);
        return codexGateway.startThread(body.cwd(), body.sandbox());
    }

    @PostMapping("/threads/resume")
    public Map<String, Object> resumeThread(
            @RequestBody ResumeThreadRequest body,
            HttpServletRequest request) {
        authenticate(request);
        return codexGateway.resumeThread(
                body.threadId(),
                body.cwd(),
                body.sandbox());
    }

    @PostMapping("/turns")
    public Map<String, Object> startTurn(
            @RequestBody StartTurnRequest body,
            HttpServletRequest request) {
        long ownerId = currentUserId(request);
        List<CodexAttachment> attachments =
                attachmentService.resolve(
                        ownerId,
                        body.workspaceId(),
                        body.attachmentIds());
        return codexGateway.startTurn(
                body.threadId(),
                body.text(),
                body.cwd(),
                attachments,
                body.model(),
                body.effort(),
                body.writableRoots());
    }

    @PostMapping("/turns/interrupt")
    public Map<String, Object> interruptTurn(
            @RequestBody InterruptTurnRequest body,
            HttpServletRequest request) {
        authenticate(request);
        return codexGateway.interruptTurn(body.threadId(), body.turnId());
    }

    @PostMapping("/turns/steer")
    public Map<String, Object> steerTurn(
            @RequestBody SteerTurnRequest body,
            HttpServletRequest request) {
        long ownerId = currentUserId(request);
        List<CodexAttachment> attachments =
                attachmentService.resolve(
                        ownerId,
                        body.workspaceId(),
                        body.attachmentIds());
        return codexGateway.steerTurn(
                body.threadId(),
                body.expectedTurnId(),
                body.text(),
                attachments);
    }

    @PostMapping("/threads/name")
    public Map<String, Object> setThreadName(
            @RequestBody SetThreadNameRequest body,
            HttpServletRequest request) {
        authenticate(request);
        return codexGateway.setThreadName(body.threadId(), body.name());
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(HttpServletRequest request) {
        authenticate(request);
        SseEmitter emitter = new SseEmitter(0L);
        AtomicReference<Runnable> unsubscribe = new AtomicReference<>();

        unsubscribe.set(codexGateway.subscribe(event -> {
            try {
                emitter.send(SseEmitter.event().data(event));
            } catch (IOException | IllegalStateException exception) {
                Runnable cleanup = unsubscribe.get();
                if (cleanup != null) {
                    cleanup.run();
                }
                emitter.complete();
            }
        }));

        Runnable cleanup = () -> {
            Runnable current = unsubscribe.getAndSet(null);
            if (current != null) {
                current.run();
            }
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());
        return emitter;
    }

    private void authenticate(HttpServletRequest request) {
        currentUserId(request);
    }

    private long currentUserId(HttpServletRequest request) {
        return authService.currentSession(
                sessionCookieReader.readToken(request)).user().id();
    }

    public record StatusResponse(
            boolean available,
            boolean connected,
            String executable) {
    }

    public record DirectoryPickerResponse(String path) {
    }

    public record AttachmentResponse(
            String id,
            String name,
            String contentType,
            long size,
            String kind) {
    }

    public record StartThreadRequest(String cwd, String sandbox) {
    }

    public record ResumeThreadRequest(
            String threadId,
            String cwd,
            String sandbox) {
    }

    public record StartTurnRequest(
            String workspaceId,
            String threadId,
            String text,
            String cwd,
            List<String> attachmentIds,
            String model,
            String effort,
            List<String> writableRoots) {
    }

    public record InterruptTurnRequest(String threadId, String turnId) {
    }

    public record SteerTurnRequest(
            String workspaceId,
            String threadId,
            String expectedTurnId,
            String text,
            List<String> attachmentIds) {
    }

    public record SetThreadNameRequest(String threadId, String name) {
    }
}
