package com.pydlove.workcove.mail.interfaces.dto;

public record MailSyncRequest(
        String workspaceId,
        long accountId,
        String folder,
        boolean flagged
) {
}
