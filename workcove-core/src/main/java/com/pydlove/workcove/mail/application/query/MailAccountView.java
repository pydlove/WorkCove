package com.pydlove.workcove.mail.application.query;

import com.pydlove.workcove.mail.domain.model.MailAccount;

import java.time.Instant;

public record MailAccountView(
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

    public static MailAccountView from(MailAccount account) {
        return new MailAccountView(
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
                !account.passwordCiphertext().isBlank(),
                account.createdAt(),
                account.updatedAt());
    }
}
