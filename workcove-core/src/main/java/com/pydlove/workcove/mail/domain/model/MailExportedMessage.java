package com.pydlove.workcove.mail.domain.model;

public record MailExportedMessage(
        byte[] content,
        String filename
) {
}
