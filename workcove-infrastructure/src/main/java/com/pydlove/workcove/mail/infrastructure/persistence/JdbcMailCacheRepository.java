package com.pydlove.workcove.mail.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pydlove.workcove.mail.domain.model.MailAttachment;
import com.pydlove.workcove.mail.domain.model.MailFolder;
import com.pydlove.workcove.mail.domain.model.MailFolderRole;
import com.pydlove.workcove.mail.domain.model.MailMessageDetail;
import com.pydlove.workcove.mail.domain.model.MailMessagePage;
import com.pydlove.workcove.mail.domain.model.MailMessageSummary;
import com.pydlove.workcove.mail.domain.model.MailRecipient;
import com.pydlove.workcove.mail.domain.model.MailSyncMessage;
import com.pydlove.workcove.mail.domain.repository.MailCacheRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcMailCacheRepository implements MailCacheRepository {

    private static final TypeReference<List<MailRecipient>> RECIPIENTS_TYPE =
            new TypeReference<>() {
            };
    private static final TypeReference<List<MailAttachment>> ATTACHMENTS_TYPE =
            new TypeReference<>() {
            };

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcMailCacheRepository(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<MailFolder> findFolders(
            long ownerId,
            String workspaceId,
            long accountId) {
        return jdbc.query(
                """
                SELECT name, path, `role`, unread_count, total_count
                FROM mail_folder
                WHERE owner_id = ? AND workspace_id = ? AND account_id = ?
                ORDER BY CASE `role`
                    WHEN 'INBOX' THEN 0
                    WHEN 'FLAGGED' THEN 1
                    WHEN 'SENT' THEN 2
                    WHEN 'DRAFTS' THEN 3
                    WHEN 'TRASH' THEN 4
                    WHEN 'JUNK' THEN 5
                    ELSE 6
                END, name
                """,
                (result, row) -> new MailFolder(
                        result.getString("name"),
                        result.getString("path"),
                        MailFolderRole.valueOf(result.getString("role")),
                        result.getInt("unread_count"),
                        result.getInt("total_count")),
                ownerId,
                workspaceId,
                accountId);
    }

    @Override
    @Transactional
    public void replaceFolders(
            long ownerId,
            String workspaceId,
            long accountId,
            List<MailFolder> folders,
            Instant syncedAt) {
        jdbc.batchUpdate(
                """
                INSERT INTO mail_folder (
                    owner_id, workspace_id, account_id, name, path, `role`,
                    unread_count, total_count, synced_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    name = VALUES(name),
                    `role` = VALUES(`role`),
                    unread_count = VALUES(unread_count),
                    total_count = VALUES(total_count),
                    synced_at = VALUES(synced_at)
                """,
                folders.stream()
                        .map((folder) -> new Object[] {
                                ownerId,
                                workspaceId,
                                accountId,
                                folder.name(),
                                folder.path(),
                                folder.role().name(),
                                folder.unreadCount(),
                                folder.totalCount(),
                                timestamp(syncedAt),
                        })
                        .toList());
        // Keep folders omitted by a partial or transient server response.
        // Deleting a folder also cascades its cached messages.
    }

    @Override
    public MailMessagePage findMessages(
            long ownerId,
            String workspaceId,
            long accountId,
            String folderPath,
            boolean flagged,
            int limit,
            int offset) {
        String scope = flagged
                ? "m.starred = TRUE"
                : "f.path = ?";
        List<Object> args = new ArrayList<>();
        args.add(ownerId);
        args.add(workspaceId);
        args.add(accountId);
        if (!flagged) args.add(folderPath);

        Integer total = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM mail_message m
                JOIN mail_folder f ON f.id = m.folder_id
                WHERE f.owner_id = ? AND f.workspace_id = ?
                      AND f.account_id = ?
                      AND %s
                """.formatted(scope),
                Integer.class,
                args.toArray());
        args.add(limit);
        args.add(offset);
        List<MailMessageSummary> messages = jdbc.query(
                """
                SELECT f.path, m.uid, m.message_id, m.from_name,
                       m.from_address, m.subject, m.received_at, m.preview,
                       m.unread, m.starred, m.attachment_count
                FROM mail_message m
                JOIN mail_folder f ON f.id = m.folder_id
                WHERE f.owner_id = ? AND f.workspace_id = ?
                      AND f.account_id = ?
                      AND %s
                ORDER BY m.received_at DESC, m.uid DESC
                LIMIT ? OFFSET ?
                """.formatted(scope),
                (result, row) -> new MailMessageSummary(
                        result.getString("path"),
                        result.getString("uid"),
                        result.getString("message_id"),
                        result.getString("from_name"),
                        result.getString("from_address"),
                        result.getString("subject"),
                        instant(result, "received_at"),
                        result.getString("preview"),
                        result.getBoolean("unread"),
                        result.getBoolean("starred"),
                        result.getInt("attachment_count")),
                args.toArray());
        return new MailMessagePage(
                messages,
                total == null ? 0 : total,
                offset,
                limit);
    }

    @Override
    public Optional<MailMessageDetail> findMessage(
            long ownerId,
            String workspaceId,
            long accountId,
            String folderPath,
            String uid) {
        return jdbc.query(
                """
                SELECT f.path, m.uid, m.message_id, m.from_name,
                       m.from_address, m.to_recipients, m.cc_recipients,
                       m.subject, m.received_at, m.content, m.html,
                       m.starred, m.attachments
                FROM mail_message m
                JOIN mail_folder f ON f.id = m.folder_id
                WHERE f.owner_id = ? AND f.workspace_id = ?
                      AND f.account_id = ? AND f.path = ? AND m.uid = ?
                """,
                (result, row) -> new MailMessageDetail(
                        result.getString("path"),
                        result.getString("uid"),
                        result.getString("message_id"),
                        result.getString("from_name"),
                        result.getString("from_address"),
                        readJson(
                                result.getString("to_recipients"),
                                RECIPIENTS_TYPE),
                        readJson(
                                result.getString("cc_recipients"),
                                RECIPIENTS_TYPE),
                        result.getString("subject"),
                        instant(result, "received_at"),
                        result.getString("content"),
                        result.getBoolean("html"),
                        result.getBoolean("starred"),
                        readJson(
                                result.getString("attachments"),
                                ATTACHMENTS_TYPE)),
                ownerId,
                workspaceId,
                accountId,
                folderPath,
                uid).stream().findFirst();
    }

    @Override
    public void updateMessageDetail(
            long ownerId,
            String workspaceId,
            long accountId,
            String folderPath,
            MailMessageDetail detail,
            Instant syncedAt) {
        jdbc.update(
                """
                UPDATE mail_message m
                JOIN mail_folder f ON f.id = m.folder_id
                SET m.to_recipients = ?,
                    m.cc_recipients = ?,
                    m.preview = ?,
                    m.content = ?,
                    m.html = ?,
                    m.attachment_count = ?,
                    m.attachments = ?,
                    m.synced_at = ?
                WHERE f.owner_id = ? AND f.workspace_id = ?
                      AND f.account_id = ? AND f.path = ?
                      AND m.uid = ?
                """,
                writeJson(detail.to()),
                writeJson(detail.cc()),
                preview(detail),
                detail.content(),
                detail.html(),
                detail.attachments().size(),
                writeJson(detail.attachments()),
                timestamp(syncedAt),
                ownerId,
                workspaceId,
                accountId,
                folderPath,
                detail.uid());
    }

    @Override
    public void updateMessagePreview(
            long ownerId,
            String workspaceId,
            long accountId,
            MailMessageSummary summary,
            Instant syncedAt) {
        jdbc.update(
                """
                UPDATE mail_message m
                JOIN mail_folder f ON f.id = m.folder_id
                SET m.preview = ?,
                    m.attachment_count = ?,
                    m.synced_at = ?
                WHERE f.owner_id = ? AND f.workspace_id = ?
                      AND f.account_id = ? AND f.path = ? AND m.uid = ?
                """,
                truncate(summary.preview(), 1000),
                summary.attachmentCount(),
                timestamp(syncedAt),
                ownerId,
                workspaceId,
                accountId,
                summary.folderPath(),
                summary.uid());
    }

    @Override
    @Transactional
    public void replaceMessages(
            long ownerId,
            String workspaceId,
            long accountId,
            String folderPath,
            List<MailSyncMessage> messages,
            Instant syncedAt) {
        if (messages.isEmpty()) return;
        long folderId = folderId(
                ownerId,
                workspaceId,
                accountId,
                folderPath,
                syncedAt);
        jdbc.batchUpdate(
                """
                INSERT INTO mail_message (
                    folder_id, uid, message_id, from_name, from_address,
                    to_recipients, cc_recipients, subject, received_at,
                    preview, content, html, unread, starred,
                    attachment_count, attachments, synced_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    message_id = VALUES(message_id),
                    from_name = VALUES(from_name),
                    from_address = VALUES(from_address),
                    to_recipients = IF(
                        VALUES(content) = '',
                        to_recipients,
                        VALUES(to_recipients)
                    ),
                    cc_recipients = IF(
                        VALUES(content) = '',
                        cc_recipients,
                        VALUES(cc_recipients)
                    ),
                    subject = VALUES(subject),
                    received_at = VALUES(received_at),
                    preview = IF(
                        VALUES(preview) = '',
                        preview,
                        VALUES(preview)
                    ),
                    content = IF(
                        VALUES(content) = '',
                        content,
                        VALUES(content)
                    ),
                    html = IF(
                        VALUES(content) = '',
                        html,
                        VALUES(html)
                    ),
                    unread = VALUES(unread),
                    starred = VALUES(starred),
                    attachment_count = IF(
                        VALUES(attachments) = '[]',
                        attachment_count,
                        VALUES(attachment_count)
                    ),
                    attachments = IF(
                        VALUES(attachments) = '[]',
                        attachments,
                        VALUES(attachments)
                    ),
                    synced_at = VALUES(synced_at)
                """,
                messages.stream()
                        .map((message) -> messageRow(
                                folderId,
                                message,
                                syncedAt))
                        .toList());
        // Syncs fetch only the latest window, so older cached messages must
        // remain available when the server is temporarily unreachable.
    }

    @Override
    public void setStarred(
            long ownerId,
            String workspaceId,
            long accountId,
            String folderPath,
            String uid,
            boolean starred) {
        updateMessage(
                "starred = ?",
                starred,
                ownerId,
                workspaceId,
                accountId,
                folderPath,
                uid);
    }

    @Override
    public void setSeen(
            long ownerId,
            String workspaceId,
            long accountId,
            String folderPath,
            String uid,
            boolean seen) {
        updateMessage(
                "unread = ?",
                !seen,
                ownerId,
                workspaceId,
                accountId,
                folderPath,
                uid);
    }

    @Override
    public void markAllSeen(
            long ownerId,
            String workspaceId,
            long accountId,
            String folderPath) {
        jdbc.update(
                """
                UPDATE mail_message m
                JOIN mail_folder f ON f.id = m.folder_id
                SET m.unread = FALSE
                WHERE f.owner_id = ? AND f.workspace_id = ?
                      AND f.account_id = ? AND f.path = ?
                """,
                ownerId,
                workspaceId,
                accountId,
                folderPath);
    }

    @Override
    public void deleteMessage(
            long ownerId,
            String workspaceId,
            long accountId,
            String folderPath,
            String uid) {
        jdbc.update(
                """
                DELETE m
                FROM mail_message m
                JOIN mail_folder f ON f.id = m.folder_id
                WHERE f.owner_id = ? AND f.workspace_id = ?
                      AND f.account_id = ? AND f.path = ? AND m.uid = ?
                """,
                ownerId,
                workspaceId,
                accountId,
                folderPath,
                uid);
    }

    private void updateMessage(
            String assignment,
            boolean value,
            long ownerId,
            String workspaceId,
            long accountId,
            String folderPath,
            String uid) {
        jdbc.update(
                """
                UPDATE mail_message m
                JOIN mail_folder f ON f.id = m.folder_id
                SET m.%s
                WHERE f.owner_id = ? AND f.workspace_id = ?
                      AND f.account_id = ? AND f.path = ? AND m.uid = ?
                """.formatted(assignment),
                value,
                ownerId,
                workspaceId,
                accountId,
                folderPath,
                uid);
    }

    private long folderId(
            long ownerId,
            String workspaceId,
            long accountId,
            String folderPath,
            Instant syncedAt) {
        List<Long> values = jdbc.query(
                """
                SELECT id
                FROM mail_folder
                WHERE owner_id = ? AND workspace_id = ?
                      AND account_id = ? AND path = ?
                """,
                (result, row) -> result.getLong("id"),
                ownerId,
                workspaceId,
                accountId,
                folderPath);
        if (!values.isEmpty()) return values.get(0);

        MailFolderRole role = "INBOX".equalsIgnoreCase(folderPath)
                ? MailFolderRole.INBOX
                : MailFolderRole.CUSTOM;
        String leafName = folderPath.substring(
                folderPath.lastIndexOf('/') + 1);
        String name = role == MailFolderRole.INBOX
                ? "收件箱"
                : truncate(
                        leafName.isBlank() ? folderPath : leafName,
                        255);
        jdbc.update(
                """
                INSERT INTO mail_folder (
                    owner_id, workspace_id, account_id, name, path, `role`,
                    unread_count, total_count, synced_at
                )
                VALUES (?, ?, ?, ?, ?, ?, 0, 0, ?)
                ON DUPLICATE KEY UPDATE synced_at = VALUES(synced_at)
                """,
                ownerId,
                workspaceId,
                accountId,
                name,
                folderPath,
                role.name(),
                timestamp(syncedAt));
        return jdbc.queryForObject(
                """
                SELECT id
                FROM mail_folder
                WHERE owner_id = ? AND workspace_id = ?
                      AND account_id = ? AND path = ?
                """,
                Long.class,
                ownerId,
                workspaceId,
                accountId,
                folderPath);
    }

    private Object[] messageRow(
            long folderId,
            MailSyncMessage message,
            Instant syncedAt) {
        MailMessageSummary summary = message.summary();
        MailMessageDetail detail = message.detail();
        return new Object[] {
                folderId,
                summary.uid(),
                truncate(summary.messageId(), 998),
                truncate(summary.fromName(), 320),
                truncate(summary.fromAddress(), 320),
                writeJson(detail.to()),
                writeJson(detail.cc()),
                truncate(summary.subject(), 1000),
                timestamp(summary.receivedAt()),
                truncate(summary.preview(), 1000),
                detail.content(),
                detail.html(),
                summary.unread(),
                summary.starred(),
                summary.attachmentCount(),
                writeJson(detail.attachments()),
                timestamp(syncedAt),
        };
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("邮件缓存数据序列化失败", exception);
        }
    }

    private <T> T readJson(String value, TypeReference<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("邮件缓存数据读取失败", exception);
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength
                ? value
                : value.substring(0, maxLength);
    }

    private static String preview(MailMessageDetail detail) {
        String value = detail.content();
        if (detail.html()) {
            value = value.replaceAll("<[^>]+>", " ");
        }
        value = value.replaceAll("\\s+", " ").trim();
        if (!value.isBlank()) return truncate(value, 1000);
        return detail.attachments().isEmpty()
                ? "无正文"
                : "包含 " + detail.attachments().size() + " 个附件";
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private static Instant instant(
            java.sql.ResultSet result,
            String column) throws java.sql.SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
