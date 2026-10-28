package com.pydlove.workcove.mail.domain.model;

public record MailConnectionSettings(
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
