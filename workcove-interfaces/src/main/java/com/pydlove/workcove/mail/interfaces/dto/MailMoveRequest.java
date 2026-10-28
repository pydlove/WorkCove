package com.pydlove.workcove.mail.interfaces.dto;

public record MailMoveRequest(
        String workspaceId,
        long accountId,
        String folder,
        String uid,
        String targetFolder
) {
}
