package com.pydlove.workcove.mail.domain.repository;

import com.pydlove.workcove.mail.domain.model.MailFolder;
import com.pydlove.workcove.mail.domain.model.MailMessageDetail;
import com.pydlove.workcove.mail.domain.model.MailMessagePage;
import com.pydlove.workcove.mail.domain.model.MailMessageSummary;
import com.pydlove.workcove.mail.domain.model.MailSyncMessage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MailCacheRepository {

    List<MailFolder> findFolders(
            long ownerId,
            String workspaceId,
            long accountId);

    void replaceFolders(
            long ownerId,
            String workspaceId,
            long accountId,
            List<MailFolder> folders,
            Instant syncedAt);

    MailMessagePage findMessages(
            long ownerId,
            String workspaceId,
            long accountId,
            String folderPath,
            boolean flagged,
            int limit,
            int offset);

    Optional<MailMessageDetail> findMessage(
            long ownerId,
            String workspaceId,
            long accountId,
            String folderPath,
            String uid);

    void updateMessageDetail(
            long ownerId,
            String workspaceId,
            long accountId,
            String folderPath,
            MailMessageDetail detail,
            Instant syncedAt);

    void updateMessagePreview(
            long ownerId,
            String workspaceId,
            long accountId,
            MailMessageSummary summary,
            Instant syncedAt);

    void replaceMessages(
            long ownerId,
            String workspaceId,
            long accountId,
            String folderPath,
            List<MailSyncMessage> messages,
            Instant syncedAt);

    void setStarred(
            long ownerId,
            String workspaceId,
            long accountId,
            String folderPath,
            String uid,
            boolean starred);

    void setSeen(
            long ownerId,
            String workspaceId,
            long accountId,
            String folderPath,
            String uid,
            boolean seen);

    void markAllSeen(
            long ownerId,
            String workspaceId,
            long accountId,
            String folderPath);

    void deleteMessage(
            long ownerId,
            String workspaceId,
            long accountId,
            String folderPath,
            String uid);
}
