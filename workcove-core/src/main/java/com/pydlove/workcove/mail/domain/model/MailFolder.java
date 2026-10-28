package com.pydlove.workcove.mail.domain.model;

public record MailFolder(
        String name,
        String path,
        MailFolderRole role,
        int unreadCount,
        int totalCount
) {
}
