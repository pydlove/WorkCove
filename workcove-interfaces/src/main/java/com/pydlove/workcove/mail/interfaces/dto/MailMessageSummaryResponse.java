package com.pydlove.workcove.mail.interfaces.dto;

import com.pydlove.workcove.mail.domain.model.MailMessageSummary;

import java.time.Instant;

public record MailMessageSummaryResponse(
        String folderPath,
        String uid,
        String messageId,
        String fromName,
        String fromAddress,
        String subject,
        Instant receivedAt,
        String preview,
        boolean unread,
        boolean starred,
        int attachmentCount
) {

    public static MailMessageSummaryResponse from(
            MailMessageSummary message) {
        return new MailMessageSummaryResponse(
                message.folderPath(),
                message.uid(),
                message.messageId(),
                message.fromName(),
                message.fromAddress(),
                message.subject(),
                message.receivedAt(),
                message.preview(),
                message.unread(),
                message.starred(),
                message.attachmentCount());
    }
}
