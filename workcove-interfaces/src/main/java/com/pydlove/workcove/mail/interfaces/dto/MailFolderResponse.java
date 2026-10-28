package com.pydlove.workcove.mail.interfaces.dto;

import com.pydlove.workcove.mail.domain.model.MailFolder;

public record MailFolderResponse(
        String name,
        String path,
        String role,
        int unreadCount,
        int totalCount
) {

    public static MailFolderResponse from(MailFolder folder) {
        return new MailFolderResponse(
                folder.name(),
                folder.path(),
                folder.role().name(),
                folder.unreadCount(),
                folder.totalCount());
    }
}
