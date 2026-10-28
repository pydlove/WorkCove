package com.pydlove.workcove.mail.interfaces.dto;

public record MailMarkAllReadRequest(
        String workspaceId,
        long accountId,
        String folder
) {
}
