package com.pydlove.workcove.mail.interfaces.dto;

public record MailReplyRequest(
        String workspaceId,
        long accountId,
        String folder,
        String uid,
        String body
) {
}
