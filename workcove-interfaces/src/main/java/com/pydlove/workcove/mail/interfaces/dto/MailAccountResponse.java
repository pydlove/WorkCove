package com.pydlove.workcove.mail.interfaces.dto;

import com.pydlove.workcove.mail.application.query.MailAccountView;

import java.time.Instant;

public record MailAccountResponse(
        long id,
        String displayName,
        String emailAddress,
        String username,
        String imapHost,
        int imapPort,
        boolean imapSsl,
        String smtpHost,
        int smtpPort,
        boolean smtpSsl,
        boolean smtpAuth,
        boolean hasPassword,
        Instant createdAt,
        Instant updatedAt
) {

    public static MailAccountResponse from(MailAccountView account) {
        return new MailAccountResponse(
                account.id(),
                account.displayName(),
                account.emailAddress(),
                account.username(),
                account.imapHost(),
                account.imapPort(),
                account.imapSsl(),
                account.smtpHost(),
                account.smtpPort(),
                account.smtpSsl(),
                account.smtpAuth(),
                account.hasPassword(),
                account.createdAt(),
                account.updatedAt());
    }
}
