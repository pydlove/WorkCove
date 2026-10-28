package com.pydlove.workcove.mail.interfaces.rest;

import com.pydlove.workcove.identity.application.command.AuthService;
import com.pydlove.workcove.identity.interfaces.security.SessionCookieReader;
import com.pydlove.workcove.mail.application.MailService;
import com.pydlove.workcove.mail.application.command.MailAccountCommand;
import com.pydlove.workcove.mail.application.command.MailRuleCommand;
import com.pydlove.workcove.mail.domain.model.MailExportedMessage;
import com.pydlove.workcove.mail.domain.model.MailOutgoingMessage;
import com.pydlove.workcove.mail.domain.model.MailRecipient;
import com.pydlove.workcove.mail.interfaces.dto.MailAccountRequest;
import com.pydlove.workcove.mail.interfaces.dto.MailAccountResponse;
import com.pydlove.workcove.mail.interfaces.dto.MailFolderRequest;
import com.pydlove.workcove.mail.interfaces.dto.MailFolderResponse;
import com.pydlove.workcove.mail.interfaces.dto.MailCreateFolderRequest;
import com.pydlove.workcove.mail.interfaces.dto.MailInboxRequest;
import com.pydlove.workcove.mail.interfaces.dto.MailMarkAllReadRequest;
import com.pydlove.workcove.mail.interfaces.dto.MailMessageDetailResponse;
import com.pydlove.workcove.mail.interfaces.dto.MailMessagePageResponse;
import com.pydlove.workcove.mail.interfaces.dto.MailMessageRequest;
import com.pydlove.workcove.mail.interfaces.dto.MailMoveRequest;
import com.pydlove.workcove.mail.interfaces.dto.MailReadStateRequest;
import com.pydlove.workcove.mail.interfaces.dto.MailReplyRequest;
import com.pydlove.workcove.mail.interfaces.dto.MailSendRequest;
import com.pydlove.workcove.mail.interfaces.dto.MailStarRequest;
import com.pydlove.workcove.mail.interfaces.dto.MailSyncRequest;
import com.pydlove.workcove.mail.interfaces.dto.MailSyncResponse;
import com.pydlove.workcove.mail.interfaces.dto.MailRuleRequest;
import com.pydlove.workcove.mail.interfaces.dto.MailRuleResponse;
import com.pydlove.workcove.shared.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/mail")
public class MailController {

    private static final String ACTION = "manage-mail";

    private final MailService mailService;
    private final AuthService authService;
    private final SessionCookieReader sessionCookieReader;

    public MailController(
            MailService mailService,
            AuthService authService,
            SessionCookieReader sessionCookieReader) {
        this.mailService = mailService;
        this.authService = authService;
        this.sessionCookieReader = sessionCookieReader;
    }

    @GetMapping("/accounts")
    public List<MailAccountResponse> accounts(
            @RequestParam String workspaceId,
            HttpServletRequest request) {
        return mailService.accounts(
                        currentUserId(request),
                        workspaceId).stream()
                .map(MailAccountResponse::from)
                .toList();
    }

    @PutMapping("/accounts")
    public MailAccountResponse saveAccount(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @RequestBody MailAccountRequest body,
            HttpServletRequest request) {
        validateAction(action);
        return MailAccountResponse.from(mailService.saveAccount(
                currentUserId(request),
                body.workspaceId(),
                new MailAccountCommand(
                        body.id(),
                        body.displayName(),
                        body.emailAddress(),
                        body.username(),
                        body.password(),
                        body.imapHost(),
                        body.imapPort(),
                        body.imapSsl(),
                        body.smtpHost(),
                        body.smtpPort(),
                        body.smtpSsl(),
                        body.smtpAuth())));
    }

    @DeleteMapping("/accounts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @PathVariable long id,
            @RequestParam String workspaceId,
            HttpServletRequest request) {
        validateAction(action);
        mailService.deleteAccount(
                currentUserId(request),
                workspaceId,
                id);
    }

    @PostMapping({"/inbox", "/messages"})
    public MailMessagePageResponse inbox(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @RequestBody MailInboxRequest body,
            HttpServletRequest request) {
        validateAction(action);
        return MailMessagePageResponse.from(mailService.messages(
                currentUserId(request),
                body.workspaceId(),
                body.accountId(),
                body.folder(),
                body.flagged(),
                body.limit(),
                body.offset()));
    }

    @PostMapping("/sync")
    public MailSyncResponse sync(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @RequestBody MailSyncRequest body,
            HttpServletRequest request) {
        validateAction(action);
        return MailSyncResponse.from(mailService.sync(
                currentUserId(request),
                body.workspaceId(),
                body.accountId(),
                body.folder(),
                body.flagged()));
    }

    @PostMapping("/folders")
    public List<MailFolderResponse> folders(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @RequestBody MailFolderRequest body,
            HttpServletRequest request) {
        validateAction(action);
        return mailService.folders(
                        currentUserId(request),
                        body.workspaceId(),
                        body.accountId()).stream()
                .map(MailFolderResponse::from)
                .toList();
    }

    @PostMapping("/folders/create")
    public MailFolderResponse createFolder(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @RequestBody MailCreateFolderRequest body,
            HttpServletRequest request) {
        validateAction(action);
        return MailFolderResponse.from(mailService.createFolder(
                currentUserId(request),
                body.workspaceId(),
                body.accountId(),
                body.name()));
    }

    @GetMapping("/rules")
    public List<MailRuleResponse> rules(
            @RequestParam String workspaceId,
            @RequestParam long accountId,
            HttpServletRequest request) {
        return mailService.rules(
                        currentUserId(request),
                        workspaceId,
                        accountId).stream()
                .map(MailRuleResponse::from)
                .toList();
    }

    @PutMapping("/rules")
    public MailRuleResponse saveRule(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @RequestBody MailRuleRequest body,
            HttpServletRequest request) {
        validateAction(action);
        return MailRuleResponse.from(mailService.saveRule(
                currentUserId(request),
                body.workspaceId(),
                body.accountId(),
                new MailRuleCommand(
                        body.id(),
                        body.name(),
                        body.matchMode(),
                        body.senderContains(),
                        body.subjectContains(),
                        body.requiresAttachment(),
                        body.targetFolder(),
                        body.enabled())));
    }

    @DeleteMapping("/rules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRule(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @PathVariable long id,
            @RequestParam String workspaceId,
            @RequestParam long accountId,
            HttpServletRequest request) {
        validateAction(action);
        mailService.deleteRule(
                currentUserId(request),
                workspaceId,
                accountId,
                id);
    }

    @PostMapping("/message")
    public MailMessageDetailResponse message(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @RequestBody MailMessageRequest body,
            HttpServletRequest request) {
        validateAction(action);
        return MailMessageDetailResponse.from(mailService.message(
                currentUserId(request),
                body.workspaceId(),
                body.accountId(),
                body.folder(),
                body.uid()));
    }

    @PostMapping("/reply")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reply(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @RequestBody MailReplyRequest body,
            HttpServletRequest request) {
        validateAction(action);
        mailService.reply(
                currentUserId(request),
                body.workspaceId(),
                body.accountId(),
                body.folder(),
                body.uid(),
                body.body());
    }

    @PostMapping("/star")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void star(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @RequestBody MailStarRequest body,
            HttpServletRequest request) {
        validateAction(action);
        mailService.setStarred(
                currentUserId(request),
                body.workspaceId(),
                body.accountId(),
                body.folder(),
                body.uid(),
                body.starred());
    }

    @PostMapping("/send")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void send(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @RequestBody MailSendRequest body,
            HttpServletRequest request) {
        validateAction(action);
        mailService.send(
                currentUserId(request),
                body.workspaceId(),
                body.accountId(),
                new MailOutgoingMessage(
                        recipients(body.to()),
                        recipients(body.cc()),
                        body.subject(),
                        body.plainBody(),
                        body.htmlBody(),
                        body.inReplyTo(),
                        body.readReceipt()));
    }

    @PostMapping("/read-state")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readState(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @RequestBody MailReadStateRequest body,
            HttpServletRequest request) {
        validateAction(action);
        mailService.setSeen(
                currentUserId(request),
                body.workspaceId(),
                body.accountId(),
                body.folder(),
                body.uid(),
                body.seen());
    }

    @PostMapping("/mark-all-read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @RequestBody MailMarkAllReadRequest body,
            HttpServletRequest request) {
        validateAction(action);
        mailService.markAllSeen(
                currentUserId(request),
                body.workspaceId(),
                body.accountId(),
                body.folder());
    }

    @PostMapping("/move")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void move(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @RequestBody MailMoveRequest body,
            HttpServletRequest request) {
        validateAction(action);
        mailService.move(
                currentUserId(request),
                body.workspaceId(),
                body.accountId(),
                body.folder(),
                body.uid(),
                body.targetFolder());
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam String workspaceId,
            @RequestParam long accountId,
            @RequestParam String folder,
            @RequestParam String uid,
            HttpServletRequest request) {
        MailExportedMessage exported = mailService.export(
                currentUserId(request),
                workspaceId,
                accountId,
                folder,
                uid);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(
                                        exported.filename(),
                                        StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(exported.content());
    }

    private List<MailRecipient> recipients(
            List<MailSendRequest.Recipient> values) {
        if (values == null) return List.of();
        return values.stream()
                .map((recipient) -> new MailRecipient(
                        recipient.name(),
                        recipient.address()))
                .toList();
    }

    private long currentUserId(HttpServletRequest request) {
        return authService.currentSession(
                sessionCookieReader.readToken(request)).user().id();
    }

    private void validateAction(String action) {
        if (!ACTION.equals(action)) {
            throw new DomainException("不支持的邮件操作");
        }
    }
}
