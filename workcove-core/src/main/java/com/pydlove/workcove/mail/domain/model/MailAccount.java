package com.pydlove.workcove.mail.domain.model;

import java.time.Instant;

public record MailAccount(
        Long id,
        long ownerId,
        String workspaceId,
        String displayName,
        String emailAddress,
        String username,
        String passwordCiphertext,
        String imapHost,
        int imapPort,
        boolean imapSsl,
        String smtpHost,
        int smtpPort,
        boolean smtpSsl,
        boolean smtpAuth,
        Instant createdAt,
        Instant updatedAt
) {
}
