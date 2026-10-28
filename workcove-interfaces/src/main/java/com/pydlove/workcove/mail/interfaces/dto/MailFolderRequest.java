package com.pydlove.workcove.mail.interfaces.dto;

public record MailFolderRequest(
        String workspaceId,
        long accountId
) {
}
