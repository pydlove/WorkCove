package com.pydlove.workcove.mail.interfaces.dto;

public record MailAccountRequest(
        Long id,
        String workspaceId,
        String displayName,
        String emailAddress,
        String username,
        String password,
        String imapHost,
        int imapPort,
        boolean imapSsl,
        String smtpHost,
        int smtpPort,
        boolean smtpSsl,
        boolean smtpAuth
) {
}
