package com.pydlove.workcove.mail.application.command;

public record MailAccountCommand(
        Long id,
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
