package com.pydlove.workcove.mail.interfaces.dto;

public record MailCreateFolderRequest(
        String workspaceId,
        long accountId,
        String name
) {
}
