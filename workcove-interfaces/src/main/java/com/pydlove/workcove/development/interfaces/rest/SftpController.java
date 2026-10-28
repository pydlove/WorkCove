package com.pydlove.workcove.development.interfaces.rest;

import com.pydlove.workcove.development.application.SftpService;
import com.pydlove.workcove.development.domain.port.SftpGateway;
import com.pydlove.workcove.identity.application.command.AuthService;
import com.pydlove.workcove.identity.interfaces.security.SessionCookieReader;
import com.pydlove.workcove.shared.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/development/servers/{serverId}/files")
public class SftpController {

    private static final String ACTION = "manage-server-files";

    private final SftpService sftpService;
    private final AuthService authService;
    private final SessionCookieReader sessionCookieReader;

    public SftpController(
            SftpService sftpService,
            AuthService authService,
            SessionCookieReader sessionCookieReader) {
        this.sftpService = sftpService;
        this.authService = authService;
        this.sessionCookieReader = sessionCookieReader;
    }

    @GetMapping
    public List<RemoteFileResponse> list(
            @PathVariable long serverId,
            @RequestParam(defaultValue = "/") String path,
            HttpServletRequest request) {
        return sftpService.list(currentUserId(request), serverId, path).stream()
                .map(RemoteFileResponse::from)
                .toList();
    }

    @GetMapping("/download")
    public ResponseEntity<StreamingResponseBody> download(
            @PathVariable long serverId,
            @RequestParam String path,
            HttpServletRequest request) {
        SftpService.RemoteDownload download = sftpService.download(
                currentUserId(request),
                serverId,
                path);
        StreamingResponseBody body = output -> {
            try (download) {
                download.stream().transferTo(output);
            }
        };
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(download.name(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(download.size())
                .body(body);
    }

    @PostMapping(
            path = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void upload(
            @RequestHeader(value = "X-WorkCove-Action", required = false) String action,
            @PathVariable long serverId,
            @RequestParam(defaultValue = "/") String path,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) throws Exception {
        validateAction(action);
        try (InputStream input = file.getInputStream()) {
            sftpService.upload(
                    currentUserId(request),
                    serverId,
                    path,
                    file.getOriginalFilename(),
                    input,
                    file.getSize());
        }
    }

    @PostMapping("/directory")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void createDirectory(
            @RequestHeader(value = "X-WorkCove-Action", required = false) String action,
            @PathVariable long serverId,
            @RequestBody CreateDirectoryRequest body,
            HttpServletRequest request) {
        validateAction(action);
        sftpService.createDirectory(
                currentUserId(request),
                serverId,
                body.path());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @RequestHeader(value = "X-WorkCove-Action", required = false) String action,
            @PathVariable long serverId,
            @RequestParam String path,
            @RequestParam(defaultValue = "false") boolean directory,
            HttpServletRequest request) {
        validateAction(action);
        sftpService.delete(
                currentUserId(request),
                serverId,
                path,
                directory);
    }

    private long currentUserId(HttpServletRequest request) {
        return authService.currentSession(
                sessionCookieReader.readToken(request)).user().id();
    }

    private void validateAction(String action) {
        if (!ACTION.equals(action)) {
            throw new DomainException("不支持的服务器文件操作");
        }
    }

    public record RemoteFileResponse(
            String name,
            String path,
            long size,
            boolean directory,
            Instant modifiedAt) {

        private static RemoteFileResponse from(SftpGateway.RemoteFile file) {
            return new RemoteFileResponse(
                    file.name(),
                    file.path(),
                    file.size(),
                    file.directory(),
                    file.modifiedAt());
        }
    }

    public record CreateDirectoryRequest(String path) {
    }
}
