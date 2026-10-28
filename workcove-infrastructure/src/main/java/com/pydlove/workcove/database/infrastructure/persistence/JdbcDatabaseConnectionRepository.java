package com.pydlove.workcove.database.infrastructure.persistence;

import com.pydlove.workcove.database.domain.model.DatabaseConnection;
import com.pydlove.workcove.database.domain.model.DatabaseType;
import com.pydlove.workcove.database.domain.repository.DatabaseConnectionRepository;
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
public class JdbcDatabaseConnectionRepository
        implements DatabaseConnectionRepository {

    private static final RowMapper<DatabaseConnection> MAPPER =
            JdbcDatabaseConnectionRepository::map;

    private final JdbcTemplate jdbc;

    public JdbcDatabaseConnectionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<DatabaseConnection> findByOwnerAndWorkspace(
            long ownerId,
            String workspaceId) {
        return jdbc.query(
                """
                SELECT id, owner_id, workspace_id, name, database_type,
                       host, port, database_name, username,
                       password_ciphertext, ssl_enabled, created_at, updated_at
                FROM database_connection
                WHERE owner_id = ? AND workspace_id = ?
                ORDER BY updated_at DESC, id DESC
                """,
                MAPPER,
                ownerId,
                workspaceId);
    }

    @Override
    public Optional<DatabaseConnection> findByIdAndOwner(
            long id,
            long ownerId) {
        return jdbc.query(
                """
                SELECT id, owner_id, workspace_id, name, database_type,
                       host, port, database_name, username,
                       password_ciphertext, ssl_enabled, created_at, updated_at
                FROM database_connection
                WHERE id = ? AND owner_id = ?
                """,
                MAPPER,
                id,
                ownerId).stream().findFirst();
    }

    @Override
    public DatabaseConnection save(DatabaseConnection connection) {
        if (connection.getId() == null) {
            KeyHolder keys = new GeneratedKeyHolder();
            jdbc.update((jdbcConnection) -> {
                PreparedStatement statement = jdbcConnection.prepareStatement(
                        """
                        INSERT INTO database_connection (
                            owner_id, workspace_id, name, database_type,
                            host, port, database_name, username,
                            password_ciphertext, ssl_enabled,
                            created_at, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, connection.getOwnerId());
                statement.setString(2, connection.getWorkspaceId());
                statement.setString(3, connection.getName());
                statement.setString(4, connection.getType().name());
                statement.setString(5, connection.getHost());
                statement.setInt(6, connection.getPort());
                statement.setString(7, connection.getDatabaseName());
                statement.setString(8, connection.getUsername());
                statement.setString(9, connection.getPasswordCiphertext());
                statement.setBoolean(10, connection.isSslEnabled());
                statement.setTimestamp(11, timestamp(connection.getCreatedAt()));
                statement.setTimestamp(12, timestamp(connection.getUpdatedAt()));
                return statement;
            }, keys);
            Number key = keys.getKey();
            if (key == null) {
                throw new IllegalStateException("数据库连接保存失败");
            }
            return findByIdAndOwner(key.longValue(), connection.getOwnerId())
                    .orElseThrow();
        }

        int updated = jdbc.update(
                """
                UPDATE database_connection
                SET name = ?, database_type = ?, host = ?, port = ?,
                    database_name = ?, username = ?,
                    password_ciphertext = ?, ssl_enabled = ?, updated_at = ?
                WHERE id = ? AND owner_id = ?
                """,
                connection.getName(),
                connection.getType().name(),
                connection.getHost(),
                connection.getPort(),
                connection.getDatabaseName(),
                connection.getUsername(),
                connection.getPasswordCiphertext(),
                connection.isSslEnabled(),
                timestamp(connection.getUpdatedAt()),
                connection.getId(),
                connection.getOwnerId());
        if (updated != 1) {
            throw new IllegalStateException("数据库连接更新失败");
        }
        return findByIdAndOwner(connection.getId(), connection.getOwnerId())
                .orElseThrow();
    }

    @Override
    public void deleteByIdAndOwner(long id, long ownerId) {
        jdbc.update(
                "DELETE FROM database_connection WHERE id = ? AND owner_id = ?",
                id,
                ownerId);
    }

    private static DatabaseConnection map(
            ResultSet result,
            int rowNumber) throws SQLException {
        return DatabaseConnection.restore(
                result.getLong("id"),
                result.getLong("owner_id"),
                result.getString("workspace_id"),
                result.getString("name"),
                DatabaseType.valueOf(result.getString("database_type")),
                result.getString("host"),
                result.getInt("port"),
                result.getString("database_name"),
                result.getString("username"),
                result.getString("password_ciphertext"),
                result.getBoolean("ssl_enabled"),
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
