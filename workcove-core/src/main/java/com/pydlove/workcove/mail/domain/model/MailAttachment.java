package com.pydlove.workcove.mail.domain.model;

public record MailAttachment(
        String filename,
        String contentType,
        long size
) {
}
