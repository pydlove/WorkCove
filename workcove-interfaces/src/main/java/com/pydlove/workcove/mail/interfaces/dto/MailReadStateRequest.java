package com.pydlove.workcove.mail.interfaces.dto;

public record MailReadStateRequest(
        String workspaceId,
        long accountId,
        String folder,
        String uid,
        boolean seen
) {
}
