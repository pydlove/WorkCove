package com.pydlove.workcove.mail.infrastructure.persistence;

import com.pydlove.workcove.mail.domain.model.MailAccount;
import com.pydlove.workcove.mail.domain.repository.MailAccountRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcMailAccountRepository implements MailAccountRepository {

    private static final RowMapper<MailAccount> ACCOUNT_MAPPER =
            JdbcMailAccountRepository::mapAccount;

    private final JdbcTemplate jdbc;

    public JdbcMailAccountRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<MailAccount> findAccounts(long ownerId, String workspaceId) {
        return jdbc.query(
                """
                SELECT id, owner_id, workspace_id, display_name,
                       email_address, username, password_ciphertext,
                       imap_host, imap_port, imap_ssl,
                       smtp_host, smtp_port, smtp_ssl, smtp_auth,
                       created_at, updated_at
                FROM mail_account
                WHERE owner_id = ? AND workspace_id = ?
                ORDER BY updated_at DESC, id DESC
                """,
                ACCOUNT_MAPPER,
                ownerId,
                workspaceId);
    }

    @Override
    public Optional<MailAccount> findAccount(
            long ownerId,
            String workspaceId,
            long accountId) {
        return jdbc.query(
                """
                SELECT id, owner_id, workspace_id, display_name,
                       email_address, username, password_ciphertext,
                       imap_host, imap_port, imap_ssl,
                       smtp_host, smtp_port, smtp_ssl, smtp_auth,
                       created_at, updated_at
                FROM mail_account
                WHERE id = ? AND owner_id = ? AND workspace_id = ?
                """,
                ACCOUNT_MAPPER,
                accountId,
                ownerId,
                workspaceId).stream().findFirst();
    }

    @Override
    public Optional<MailAccount> findAccountByAddress(
            long ownerId,
            String workspaceId,
            String emailAddress) {
        return jdbc.query(
                """
                SELECT id, owner_id, workspace_id, display_name,
                       email_address, username, password_ciphertext,
                       imap_host, imap_port, imap_ssl,
                       smtp_host, smtp_port, smtp_ssl, smtp_auth,
                       created_at, updated_at
                FROM mail_account
                WHERE owner_id = ? AND workspace_id = ? AND email_address = ?
                """,
                ACCOUNT_MAPPER,
                ownerId,
                workspaceId,
                emailAddress).stream().findFirst();
    }

    @Override
    public MailAccount save(MailAccount account) {
        if (account.id() == null) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        """
                        INSERT INTO mail_account (
                            owner_id, workspace_id, display_name, email_address,
                            username, password_ciphertext, imap_host, imap_port,
                            imap_ssl, smtp_host, smtp_port, smtp_ssl, smtp_auth,
                            created_at, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        Statement.RETURN_GENERATED_KEYS);
                setAccount(statement, account);
                return statement;
            }, keyHolder);
            Number key = keyHolder.getKey();
            if (key == null) {
                throw new IllegalStateException("邮箱账号保存失败");
            }
            return findAccount(
                    account.ownerId(),
                    account.workspaceId(),
                    key.longValue()).orElseThrow();
        }

        int updated = jdbc.update(
                """
                UPDATE mail_account
                SET display_name = ?, email_address = ?, username = ?,
                    password_ciphertext = ?, imap_host = ?, imap_port = ?,
                    imap_ssl = ?, smtp_host = ?, smtp_port = ?, smtp_ssl = ?,
                    smtp_auth = ?,
                    updated_at = ?
                WHERE id = ? AND owner_id = ? AND workspace_id = ?
                """,
                account.displayName(),
                account.emailAddress(),
                account.username(),
                account.passwordCiphertext(),
                account.imapHost(),
                account.imapPort(),
                account.imapSsl(),
                account.smtpHost(),
                account.smtpPort(),
                account.smtpSsl(),
                account.smtpAuth(),
                timestamp(account.updatedAt()),
                account.id(),
                account.ownerId(),
                account.workspaceId());
        if (updated != 1) {
            throw new IllegalStateException("邮箱账号更新失败");
        }
        return findAccount(
                account.ownerId(),
                account.workspaceId(),
                account.id()).orElseThrow();
    }

    @Override
    public void delete(long ownerId, String workspaceId, long accountId) {
        jdbc.update(
                """
                DELETE FROM mail_account
                WHERE id = ? AND owner_id = ? AND workspace_id = ?
                """,
                accountId,
                ownerId,
                workspaceId);
    }

    private static void setAccount(
            PreparedStatement statement,
            MailAccount account) throws java.sql.SQLException {
        int index = 1;
        statement.setLong(index++, account.ownerId());
        statement.setString(index++, account.workspaceId());
        statement.setString(index++, account.displayName());
        statement.setString(index++, account.emailAddress());
        statement.setString(index++, account.username());
        statement.setString(index++, account.passwordCiphertext());
        statement.setString(index++, account.imapHost());
        statement.setInt(index++, account.imapPort());
        statement.setBoolean(index++, account.imapSsl());
        statement.setString(index++, account.smtpHost());
        statement.setInt(index++, account.smtpPort());
        statement.setBoolean(index++, account.smtpSsl());
        statement.setBoolean(index++, account.smtpAuth());
        statement.setTimestamp(index++, timestamp(account.createdAt()));
        statement.setTimestamp(index, timestamp(account.updatedAt()));
    }

    private static MailAccount mapAccount(
            java.sql.ResultSet result,
            int rowNumber) throws java.sql.SQLException {
        return new MailAccount(
                result.getLong("id"),
                result.getLong("owner_id"),
                result.getString("workspace_id"),
                result.getString("display_name"),
                result.getString("email_address"),
                result.getString("username"),
                result.getString("password_ciphertext"),
                result.getString("imap_host"),
                result.getInt("imap_port"),
                result.getBoolean("imap_ssl"),
                result.getString("smtp_host"),
                result.getInt("smtp_port"),
                result.getBoolean("smtp_ssl"),
                result.getBoolean("smtp_auth"),
                instant(result, "created_at"),
                instant(result, "updated_at"));
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(
            java.sql.ResultSet result,
            String column) throws java.sql.SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
