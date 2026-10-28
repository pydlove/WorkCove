package com.pydlove.workcove.vault.infrastructure.persistence;

import com.pydlove.workcove.vault.domain.model.VaultChallenge;
import com.pydlove.workcove.vault.domain.model.VaultCategory;
import com.pydlove.workcove.vault.domain.model.VaultEntry;
import com.pydlove.workcove.vault.domain.model.VaultPasskey;
import com.pydlove.workcove.vault.domain.model.VaultUnlockSession;
import com.pydlove.workcove.vault.domain.repository.VaultChallengeRepository;
import com.pydlove.workcove.vault.domain.repository.VaultCategoryRepository;
import com.pydlove.workcove.vault.domain.repository.VaultEntryRepository;
import com.pydlove.workcove.vault.domain.repository.VaultPasskeyRepository;
import com.pydlove.workcove.vault.domain.repository.VaultUnlockSessionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcVaultRepository
        implements VaultEntryRepository,
        VaultCategoryRepository,
        VaultPasskeyRepository,
        VaultChallengeRepository,
        VaultUnlockSessionRepository {

    private static final RowMapper<VaultEntry> ENTRY_MAPPER =
            JdbcVaultRepository::mapEntry;
    private static final RowMapper<VaultCategory> CATEGORY_MAPPER =
            JdbcVaultRepository::mapCategory;
    private static final RowMapper<VaultPasskey> PASSKEY_MAPPER =
            JdbcVaultRepository::mapPasskey;
    private static final RowMapper<VaultChallenge> CHALLENGE_MAPPER =
            JdbcVaultRepository::mapChallenge;
    private static final RowMapper<VaultUnlockSession> SESSION_MAPPER =
            JdbcVaultRepository::mapSession;

    private final JdbcTemplate jdbc;

    public JdbcVaultRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<VaultCategory> findCategoriesByOwnerId(long ownerId) {
        return jdbc.query(
                """
                SELECT id, owner_id, name, sort_order, created_at, updated_at
                FROM vault_category
                WHERE owner_id = ?
                ORDER BY sort_order, id
                """,
                CATEGORY_MAPPER,
                ownerId);
    }

    @Override
    public Optional<VaultCategory> findCategoryByIdAndOwnerId(
            long id,
            long ownerId) {
        return jdbc.query(
                """
                SELECT id, owner_id, name, sort_order, created_at, updated_at
                FROM vault_category
                WHERE id = ? AND owner_id = ?
                """,
                CATEGORY_MAPPER,
                id,
                ownerId).stream().findFirst();
    }

    @Override
    public Optional<VaultCategory> findCategoryByNameAndOwnerId(
            String name,
            long ownerId) {
        return jdbc.query(
                """
                SELECT id, owner_id, name, sort_order, created_at, updated_at
                FROM vault_category
                WHERE name = ? AND owner_id = ?
                """,
                CATEGORY_MAPPER,
                name,
                ownerId).stream().findFirst();
    }

    @Override
    public VaultCategory saveCategory(VaultCategory category) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update((connection) -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO vault_category (
                        owner_id, name, sort_order, created_at, updated_at
                    )
                    VALUES (?, ?, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, category.ownerId());
            statement.setString(2, category.name());
            statement.setInt(3, category.sortOrder());
            statement.setTimestamp(4, timestamp(category.createdAt()));
            statement.setTimestamp(5, timestamp(category.updatedAt()));
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("密码分类创建失败");
        }
        return findCategoryByIdAndOwnerId(key.longValue(), category.ownerId())
                .orElseThrow();
    }

    @Override
    @Transactional
    public void renameCategory(
            long ownerId,
            long id,
            String currentName,
            String nextName,
            Instant updatedAt) {
        jdbc.update(
                """
                UPDATE vault_category
                SET name = ?, updated_at = ?
                WHERE id = ? AND owner_id = ?
                """,
                nextName,
                timestamp(updatedAt),
                id,
                ownerId);
        jdbc.update(
                """
                UPDATE vault_entry
                SET category = ?, updated_at = ?
                WHERE owner_id = ? AND category = ?
                """,
                nextName,
                timestamp(updatedAt),
                ownerId,
                currentName);
    }

    @Override
    @Transactional
    public void deleteCategoryAndReassignEntries(
            long ownerId,
            long id,
            String categoryName,
            String fallbackName,
            Instant updatedAt) {
        jdbc.update(
                """
                UPDATE vault_entry
                SET category = ?, updated_at = ?
                WHERE owner_id = ? AND category = ?
                """,
                fallbackName,
                timestamp(updatedAt),
                ownerId,
                categoryName);
        jdbc.update(
                "DELETE FROM vault_category WHERE id = ? AND owner_id = ?",
                id,
                ownerId);
    }

    @Override
    public List<VaultEntry> findEntriesByOwnerId(long ownerId) {
        return jdbc.query(
                """
                SELECT id, owner_id, name, username, password_ciphertext,
                       url, category, notes, created_at, updated_at
                FROM vault_entry
                WHERE owner_id = ?
                ORDER BY updated_at DESC, id DESC
                """,
                ENTRY_MAPPER,
                ownerId);
    }

    @Override
    public Optional<VaultEntry> findByIdAndOwnerId(long id, long ownerId) {
        return jdbc.query(
                """
                SELECT id, owner_id, name, username, password_ciphertext,
                       url, category, notes, created_at, updated_at
                FROM vault_entry
                WHERE id = ? AND owner_id = ?
                """,
                ENTRY_MAPPER,
                id,
                ownerId).stream().findFirst();
    }

    @Override
    public VaultEntry save(VaultEntry entry) {
        if (entry.id() == null) {
            KeyHolder keys = new GeneratedKeyHolder();
            jdbc.update((connection) -> {
                PreparedStatement statement = connection.prepareStatement(
                        """
                        INSERT INTO vault_entry (
                            owner_id, name, username, password_ciphertext,
                            url, category, notes, created_at, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, entry.ownerId());
                statement.setString(2, entry.name());
                statement.setString(3, entry.username());
                statement.setString(4, entry.passwordCiphertext());
                statement.setString(5, entry.url());
                statement.setString(6, entry.category());
                statement.setString(7, entry.notes());
                statement.setTimestamp(8, timestamp(entry.createdAt()));
                statement.setTimestamp(9, timestamp(entry.updatedAt()));
                return statement;
            }, keys);
            Number key = keys.getKey();
            if (key == null) {
                throw new IllegalStateException("密码条目创建失败");
            }
            return findByIdAndOwnerId(key.longValue(), entry.ownerId())
                    .orElseThrow();
        }
        jdbc.update(
                """
                UPDATE vault_entry
                SET name = ?,
                    username = ?,
                    password_ciphertext = ?,
                    url = ?,
                    category = ?,
                    notes = ?,
                    updated_at = ?
                WHERE id = ? AND owner_id = ?
                """,
                entry.name(),
                entry.username(),
                entry.passwordCiphertext(),
                entry.url(),
                entry.category(),
                entry.notes(),
                timestamp(entry.updatedAt()),
                entry.id(),
                entry.ownerId());
        return findByIdAndOwnerId(entry.id(), entry.ownerId())
                .orElseThrow();
    }

    @Override
    public void deleteByIdAndOwnerId(long id, long ownerId) {
        jdbc.update(
                "DELETE FROM vault_entry WHERE id = ? AND owner_id = ?",
                id,
                ownerId);
    }

    @Override
    public List<VaultPasskey> findPasskeysByOwnerId(long ownerId) {
        return jdbc.query(
                """
                SELECT id, owner_id, credential_id, registration_response,
                       sign_count, created_at, last_used_at
                FROM vault_passkey
                WHERE owner_id = ?
                ORDER BY created_at DESC, id DESC
                """,
                PASSKEY_MAPPER,
                ownerId);
    }

    @Override
    public Optional<VaultPasskey> findByCredentialIdAndOwnerId(
            String credentialId,
            long ownerId) {
        return jdbc.query(
                """
                SELECT id, owner_id, credential_id, registration_response,
                       sign_count, created_at, last_used_at
                FROM vault_passkey
                WHERE credential_id = ? AND owner_id = ?
                """,
                PASSKEY_MAPPER,
                credentialId,
                ownerId).stream().findFirst();
    }

    @Override
    public VaultPasskey save(VaultPasskey passkey) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update((connection) -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO vault_passkey (
                        owner_id, credential_id, registration_response,
                        sign_count, created_at, last_used_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, passkey.ownerId());
            statement.setString(2, passkey.credentialId());
            statement.setString(3, passkey.registrationResponse());
            statement.setLong(4, passkey.signCount());
            statement.setTimestamp(5, timestamp(passkey.createdAt()));
            statement.setTimestamp(6, timestamp(passkey.lastUsedAt()));
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("触控 ID 凭据保存失败");
        }
        return findByCredentialIdAndOwnerId(
                passkey.credentialId(),
                passkey.ownerId()).orElseThrow();
    }

    @Override
    public void updateUsage(
            String credentialId,
            long ownerId,
            long signCount,
            Instant usedAt) {
        jdbc.update(
                """
                UPDATE vault_passkey
                SET sign_count = ?, last_used_at = ?
                WHERE credential_id = ? AND owner_id = ?
                """,
                signCount,
                timestamp(usedAt),
                credentialId,
                ownerId);
    }

    @Override
    public VaultChallenge save(VaultChallenge challenge) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update((connection) -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO vault_webauthn_challenge (
                        owner_id, purpose, challenge, expires_at,
                        consumed_at, created_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, challenge.ownerId());
            statement.setString(2, challenge.purpose());
            statement.setString(3, challenge.challenge());
            statement.setTimestamp(4, timestamp(challenge.expiresAt()));
            statement.setTimestamp(5, timestamp(challenge.consumedAt()));
            statement.setTimestamp(6, timestamp(challenge.createdAt()));
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("WebAuthn 挑战保存失败");
        }
        return new VaultChallenge(
                key.longValue(),
                challenge.ownerId(),
                challenge.purpose(),
                challenge.challenge(),
                challenge.expiresAt(),
                challenge.consumedAt(),
                challenge.createdAt());
    }

    @Override
    public Optional<VaultChallenge> findLatestActive(
            long ownerId,
            String purpose,
            Instant now) {
        return jdbc.query(
                """
                SELECT id, owner_id, purpose, challenge, expires_at,
                       consumed_at, created_at
                FROM vault_webauthn_challenge
                WHERE owner_id = ?
                  AND purpose = ?
                  AND consumed_at IS NULL
                  AND expires_at > ?
                ORDER BY created_at DESC, id DESC
                LIMIT 1
                """,
                CHALLENGE_MAPPER,
                ownerId,
                purpose,
                timestamp(now)).stream().findFirst();
    }

    @Override
    public void markConsumed(long id, Instant consumedAt) {
        jdbc.update(
                """
                UPDATE vault_webauthn_challenge
                SET consumed_at = ?
                WHERE id = ? AND consumed_at IS NULL
                """,
                timestamp(consumedAt),
                id);
    }

    @Override
    public void deleteExpiredChallenges(long ownerId, Instant now) {
        jdbc.update(
                """
                DELETE FROM vault_webauthn_challenge
                WHERE owner_id = ? AND expires_at <= ?
                """,
                ownerId,
                timestamp(now));
    }

    @Override
    public VaultUnlockSession save(VaultUnlockSession session) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update((connection) -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO vault_unlock_session (
                        owner_id, token_hash, expires_at, created_at
                    )
                    VALUES (?, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, session.ownerId());
            statement.setString(2, session.tokenHash());
            statement.setTimestamp(3, timestamp(session.expiresAt()));
            statement.setTimestamp(4, timestamp(session.createdAt()));
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("保险库解锁会话保存失败");
        }
        return new VaultUnlockSession(
                key.longValue(),
                session.ownerId(),
                session.tokenHash(),
                session.expiresAt(),
                session.createdAt());
    }

    @Override
    public Optional<VaultUnlockSession> findActive(
            String tokenHash,
            Instant now) {
        return jdbc.query(
                """
                SELECT id, owner_id, token_hash, expires_at, created_at
                FROM vault_unlock_session
                WHERE token_hash = ? AND expires_at > ?
                """,
                SESSION_MAPPER,
                tokenHash,
                timestamp(now)).stream().findFirst();
    }

    @Override
    public void deleteByTokenHash(String tokenHash) {
        jdbc.update(
                "DELETE FROM vault_unlock_session WHERE token_hash = ?",
                tokenHash);
    }

    @Override
    public void deleteExpiredUnlockSessions(long ownerId, Instant now) {
        jdbc.update(
                """
                DELETE FROM vault_unlock_session
                WHERE owner_id = ? AND expires_at <= ?
                """,
                ownerId,
                timestamp(now));
    }

    private static VaultEntry mapEntry(
            ResultSet result,
            int rowNumber) throws SQLException {
        return new VaultEntry(
                result.getLong("id"),
                result.getLong("owner_id"),
                result.getString("name"),
                result.getString("username"),
                result.getString("password_ciphertext"),
                result.getString("url"),
                result.getString("category"),
                result.getString("notes"),
                instant(result, "created_at"),
                instant(result, "updated_at"));
    }

    private static VaultCategory mapCategory(
            ResultSet result,
            int rowNumber) throws SQLException {
        return new VaultCategory(
                result.getLong("id"),
                result.getLong("owner_id"),
                result.getString("name"),
                result.getInt("sort_order"),
                instant(result, "created_at"),
                instant(result, "updated_at"));
    }

    private static VaultPasskey mapPasskey(
            ResultSet result,
            int rowNumber) throws SQLException {
        return new VaultPasskey(
                result.getLong("id"),
                result.getLong("owner_id"),
                result.getString("credential_id"),
                result.getString("registration_response"),
                result.getLong("sign_count"),
                instant(result, "created_at"),
                instant(result, "last_used_at"));
    }

    private static VaultChallenge mapChallenge(
            ResultSet result,
            int rowNumber) throws SQLException {
        return new VaultChallenge(
                result.getLong("id"),
                result.getLong("owner_id"),
                result.getString("purpose"),
                result.getString("challenge"),
                instant(result, "expires_at"),
                instant(result, "consumed_at"),
                instant(result, "created_at"));
    }

    private static VaultUnlockSession mapSession(
            ResultSet result,
            int rowNumber) throws SQLException {
        return new VaultUnlockSession(
                result.getLong("id"),
                result.getLong("owner_id"),
                result.getString("token_hash"),
                instant(result, "expires_at"),
                instant(result, "created_at"));
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(ResultSet result, String column)
            throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
