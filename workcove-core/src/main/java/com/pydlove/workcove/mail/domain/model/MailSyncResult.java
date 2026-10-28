package com.pydlove.workcove.mail.domain.model;

import java.time.Instant;

public record MailSyncResult(
        int folderCount,
        int messageCount,
        Instant syncedAt
) {
}
