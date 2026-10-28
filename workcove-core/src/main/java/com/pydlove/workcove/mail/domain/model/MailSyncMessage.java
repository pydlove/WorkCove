package com.pydlove.workcove.mail.domain.model;

public record MailSyncMessage(
        MailMessageSummary summary,
        MailMessageDetail detail
) {
}
