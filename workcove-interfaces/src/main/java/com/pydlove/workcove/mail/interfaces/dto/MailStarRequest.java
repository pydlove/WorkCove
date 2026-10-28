package com.pydlove.workcove.mail.interfaces.dto;

public record MailStarRequest(
        String workspaceId,
        long accountId,
        String folder,
        String uid,
        boolean starred
) {
}
