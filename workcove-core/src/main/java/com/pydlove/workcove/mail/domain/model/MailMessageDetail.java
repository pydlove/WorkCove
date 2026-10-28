package com.pydlove.workcove.mail.domain.model;

import java.time.Instant;
import java.util.List;

public record MailMessageDetail(
        String folderPath,
        String uid,
        String messageId,
        String fromName,
        String fromAddress,
        List<MailRecipient> to,
        List<MailRecipient> cc,
        String subject,
        Instant receivedAt,
        String content,
        boolean html,
        boolean starred,
        List<MailAttachment> attachments
) {
}
