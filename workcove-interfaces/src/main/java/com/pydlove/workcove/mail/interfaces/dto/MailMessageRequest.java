package com.pydlove.workcove.mail.interfaces.dto;

public record MailMessageRequest(
        String workspaceId,
        long accountId,
        String folder,
        String uid
) {
}
