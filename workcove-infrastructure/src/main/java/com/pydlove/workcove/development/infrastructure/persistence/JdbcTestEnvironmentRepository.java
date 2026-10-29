package com.pydlove.workcove.development.infrastructure.persistence;

import com.pydlove.workcove.development.domain.model.TestEnvironment;
import com.pydlove.workcove.development.domain.repository.TestEnvironmentRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcTestEnvironmentRepository
        implements TestEnvironmentRepository {

    private static final RowMapper<TestEnvironment> MAPPER =
            JdbcTestEnvironmentRepository::map;

    private final JdbcTemplate jdbc;

    public JdbcTestEnvironmentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<TestEnvironment> findByOwnerAndWorkspace(
            long ownerId,
            String workspaceId) {
        return jdbc.query(
                """
                SELECT id, owner_id, workspace_id, name, url, username,
                       password_ciphertext, created_at, updated_at
                FROM test_environment
                WHERE owner_id = ? AND workspace_id = ?
                ORDER BY updated_at DESC, id DESC
                """,
                MAPPER,
                ownerId,
                workspaceId);
    }

    @Override
    public Optional<TestEnvironment> findByIdAndOwnerAndWorkspace(
            long id,
            long ownerId,
            String workspaceId) {
        return jdbc.query(
                """
                SELECT id, owner_id, workspace_id, name, url, username,
                       password_ciphertext, created_at, updated_at
                FROM test_environment
                WHERE id = ? AND owner_id = ? AND workspace_id = ?
                """,
                MAPPER,
                id,
                ownerId,
                workspaceId).stream().findFirst();
    }

    @Override
    public TestEnvironment save(TestEnvironment environment) {
        if (environment.getId() == null) {
            KeyHolder keys = new GeneratedKeyHolder();
            jdbc.update((connection) -> {
                PreparedStatement statement = connection.prepareStatement(
                        """
                        INSERT INTO test_environment (
                            owner_id, workspace_id, name, url, username,
                            password_ciphertext, created_at, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, environment.getOwnerId());
                statement.setString(2, environment.getWorkspaceId());
                statement.setString(3, environment.getName());
                statement.setString(4, environment.getUrl());
                statement.setString(5, environment.getUsername());
                statement.setString(6, environment.getPasswordCiphertext());
                statement.setTimestamp(7, timestamp(environment.getCreatedAt()));
                statement.setTimestamp(8, timestamp(environment.getUpdatedAt()));
                return statement;
            }, keys);
            Number key = keys.getKey();
            if (key == null) {
                throw new IllegalStateException("测试环境保存失败");
            }
            return findByIdAndOwnerAndWorkspace(
                    key.longValue(),
                    environment.getOwnerId(),
                    environment.getWorkspaceId()).orElseThrow();
        }

        int updated = jdbc.update(
                """
                UPDATE test_environment
                SET name = ?, url = ?, username = ?,
                    password_ciphertext = ?, updated_at = ?
                WHERE id = ? AND owner_id = ? AND workspace_id = ?
                """,
                environment.getName(),
                environment.getUrl(),
                environment.getUsername(),
                environment.getPasswordCiphertext(),
                timestamp(environment.getUpdatedAt()),
                environment.getId(),
                environment.getOwnerId(),
                environment.getWorkspaceId());
        if (updated != 1) {
            throw new IllegalStateException("测试环境更新失败");
        }
        return findByIdAndOwnerAndWorkspace(
                environment.getId(),
                environment.getOwnerId(),
                environment.getWorkspaceId()).orElseThrow();
    }

    @Override
    public void deleteByIdAndOwnerAndWorkspace(
            long id,
            long ownerId,
            String workspaceId) {
        jdbc.update(
                """
                DELETE FROM test_environment
                WHERE id = ? AND owner_id = ? AND workspace_id = ?
                """,
                id,
                ownerId,
                workspaceId);
    }

    private static TestEnvironment map(
            ResultSet result,
            int rowNumber) throws SQLException {
        return TestEnvironment.restore(
                result.getLong("id"),
                result.getLong("owner_id"),
                result.getString("workspace_id"),
                result.getString("name"),
                result.getString("url"),
                result.getString("username"),
                result.getString("password_ciphertext"),
                instant(result, "created_at"),
                instant(result, "updated_at"));
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private static Instant instant(ResultSet result, String column)
            throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
