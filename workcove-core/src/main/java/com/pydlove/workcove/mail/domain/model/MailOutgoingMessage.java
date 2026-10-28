package com.pydlove.workcove.mail.domain.model;

import java.util.List;

public record MailOutgoingMessage(
        List<MailRecipient> to,
        List<MailRecipient> cc,
        String subject,
        String plainBody,
        String htmlBody,
        String inReplyTo,
        boolean readReceipt
) {
}
