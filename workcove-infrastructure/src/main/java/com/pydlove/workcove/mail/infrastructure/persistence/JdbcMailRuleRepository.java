package com.pydlove.workcove.mail.infrastructure.persistence;

import com.pydlove.workcove.mail.domain.model.MailRule;
import com.pydlove.workcove.mail.domain.model.MailRuleMatchMode;
import com.pydlove.workcove.mail.domain.repository.MailRuleRepository;
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
public class JdbcMailRuleRepository implements MailRuleRepository {

    private static final RowMapper<MailRule> RULE_MAPPER =
            JdbcMailRuleRepository::mapRule;

    private final JdbcTemplate jdbc;

    public JdbcMailRuleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<MailRule> findRules(
            long ownerId,
            String workspaceId,
            long accountId) {
        return jdbc.query(
                """
                SELECT id, owner_id, workspace_id, account_id, name,
                       match_mode, sender_contains, subject_contains,
                       requires_attachment, target_folder, enabled,
                       created_at, updated_at
                FROM mail_rule
                WHERE owner_id = ? AND workspace_id = ? AND account_id = ?
                ORDER BY updated_at DESC, id DESC
                """,
                RULE_MAPPER,
                ownerId,
                workspaceId,
                accountId);
    }

    @Override
    public Optional<MailRule> findRule(
            long ownerId,
            String workspaceId,
            long accountId,
            long ruleId) {
        return jdbc.query(
                """
                SELECT id, owner_id, workspace_id, account_id, name,
                       match_mode, sender_contains, subject_contains,
                       requires_attachment, target_folder, enabled,
                       created_at, updated_at
                FROM mail_rule
                WHERE id = ? AND owner_id = ? AND workspace_id = ?
                      AND account_id = ?
                """,
                RULE_MAPPER,
                ruleId,
                ownerId,
                workspaceId,
                accountId).stream().findFirst();
    }

    @Override
    public MailRule save(MailRule rule) {
        if (rule.id() == null) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        """
                        INSERT INTO mail_rule (
                            owner_id, workspace_id, account_id, name,
                            match_mode, sender_contains, subject_contains,
                            requires_attachment, target_folder, enabled,
                            created_at, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        Statement.RETURN_GENERATED_KEYS);
                setRule(statement, rule);
                return statement;
            }, keyHolder);
            Number key = keyHolder.getKey();
            if (key == null) {
                throw new IllegalStateException("邮件规则保存失败");
            }
            return findRule(
                    rule.ownerId(),
                    rule.workspaceId(),
                    rule.accountId(),
                    key.longValue()).orElseThrow();
        }

        int updated = jdbc.update(
                """
                UPDATE mail_rule
                SET name = ?, match_mode = ?, sender_contains = ?,
                    subject_contains = ?, requires_attachment = ?,
                    target_folder = ?, enabled = ?, updated_at = ?
                WHERE id = ? AND owner_id = ? AND workspace_id = ?
                      AND account_id = ?
                """,
                rule.name(),
                rule.matchMode().name(),
                rule.senderContains(),
                rule.subjectContains(),
                rule.requiresAttachment(),
                rule.targetFolder(),
                rule.enabled(),
                timestamp(rule.updatedAt()),
                rule.id(),
                rule.ownerId(),
                rule.workspaceId(),
                rule.accountId());
        if (updated != 1) {
            throw new IllegalStateException("邮件规则更新失败");
        }
        return findRule(
                rule.ownerId(),
                rule.workspaceId(),
                rule.accountId(),
                rule.id()).orElseThrow();
    }

    @Override
    public void delete(
            long ownerId,
            String workspaceId,
            long accountId,
            long ruleId) {
        jdbc.update(
                """
                DELETE FROM mail_rule
                WHERE id = ? AND owner_id = ? AND workspace_id = ?
                      AND account_id = ?
                """,
                ruleId,
                ownerId,
                workspaceId,
                accountId);
    }

    private static void setRule(
            PreparedStatement statement,
            MailRule rule) throws java.sql.SQLException {
        int index = 1;
        statement.setLong(index++, rule.ownerId());
        statement.setString(index++, rule.workspaceId());
        statement.setLong(index++, rule.accountId());
        statement.setString(index++, rule.name());
        statement.setString(index++, rule.matchMode().name());
        statement.setString(index++, rule.senderContains());
        statement.setString(index++, rule.subjectContains());
        statement.setBoolean(index++, rule.requiresAttachment());
        statement.setString(index++, rule.targetFolder());
        statement.setBoolean(index++, rule.enabled());
        statement.setTimestamp(index++, timestamp(rule.createdAt()));
        statement.setTimestamp(index, timestamp(rule.updatedAt()));
    }

    private static MailRule mapRule(
            java.sql.ResultSet result,
            int rowNumber) throws java.sql.SQLException {
        return new MailRule(
                result.getLong("id"),
                result.getLong("owner_id"),
                result.getString("workspace_id"),
                result.getLong("account_id"),
                result.getString("name"),
                MailRuleMatchMode.valueOf(result.getString("match_mode")),
                result.getString("sender_contains"),
                result.getString("subject_contains"),
                result.getBoolean("requires_attachment"),
                result.getString("target_folder"),
                result.getBoolean("enabled"),
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
