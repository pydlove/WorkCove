package com.pydlove.workcove.mail.interfaces.dto;

public record MailInboxRequest(
        String workspaceId,
        long accountId,
        String folder,
        boolean flagged,
        int limit,
        int offset
) {
}
