package com.pydlove.workcove.mail.interfaces.dto;

import com.pydlove.workcove.mail.domain.model.MailSyncResult;

import java.time.Instant;

public record MailSyncResponse(
        int folderCount,
        int messageCount,
        Instant syncedAt
) {

    public static MailSyncResponse from(MailSyncResult result) {
        return new MailSyncResponse(
                result.folderCount(),
                result.messageCount(),
                result.syncedAt());
    }
}
