package com.pydlove.workcove.mail.domain.model;

import java.time.Instant;

public record MailMessageSummary(
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
}
