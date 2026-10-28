package com.pydlove.workcove.database.application;

import com.pydlove.workcove.database.domain.model.DatabaseConnection;
import com.pydlove.workcove.database.domain.model.DatabaseType;
import com.pydlove.workcove.database.domain.port.DatabaseQueryGateway;
import com.pydlove.workcove.database.domain.repository.DatabaseConnectionRepository;
import com.pydlove.workcove.development.domain.port.CredentialCipher;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DatabaseConnectionServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-10-27T10:00:00Z");
    private static final long OWNER_ID = 42L;

    private final InMemoryRepository repository =
            new InMemoryRepository();
    private final CapturingQueryGateway gateway =
            new CapturingQueryGateway();
    private final CredentialCipher cipher = new PrefixCipher();
    private final DatabaseConnectionService service =
            new DatabaseConnectionService(
                    repository,
                    gateway,
                    cipher,
                    Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void savesEncryptedConnectionAndKeepsPasswordOnEdit() {
        var created = service.save(
                OWNER_ID,
                "default",
                null,
                command("生产 MySQL", DatabaseType.MYSQL, "db.local", 3306, "secret"));

        assertEquals(1L, created.id());
        assertEquals(DatabaseType.MYSQL, created.type());
        assertEquals("enc:secret", repository.connection.getPasswordCiphertext());

        service.save(
                OWNER_ID,
                "default",
                created.id(),
                command("生产 MySQL", DatabaseType.MYSQL, "db.local", 3306, ""));

        assertEquals("enc:secret", repository.connection.getPasswordCiphertext());
    }

    @Test
    void executesQueryWithDecryptedPassword() {
        var created = service.save(
                OWNER_ID,
                "default",
                null,
                command(
                        "分析 ClickHouse",
                        DatabaseType.CLICKHOUSE,
                        "clickhouse.local",
                        8123,
                        "secret"));

        var result = service.execute(OWNER_ID, created.id(), "SELECT 1");

        assertEquals(true, result.resultSet());
        assertEquals("secret", gateway.lastSpec.password());
        assertEquals("SELECT 1", gateway.lastSql);
        assertNotEquals("enc:secret", gateway.lastSpec.password());
    }

    @Test
    void listsTablesAndColumnsAndPreviewsTableData() {
        var created = service.save(
                OWNER_ID,
                "default",
                null,
                command("生产 MySQL", DatabaseType.MYSQL, "db.local", 3306, "secret"));

        var schemas = service.databases(OWNER_ID, created.id());
        var tables = service.tables(OWNER_ID, created.id(), "analytics");
        var columns = service.columns(OWNER_ID, created.id(), "auth_user");
        var data = service.tableData(OWNER_ID, created.id(), "auth_user");

        assertEquals(1, schemas.size());
        assertEquals("analytics", schemas.get(0).name());
        assertEquals(1, tables.size());
        assertEquals("auth_user", tables.get(0).name());
        assertEquals("BASE TABLE", tables.get(0).type());
        assertEquals(1, columns.size());
        assertEquals("id", columns.get(0).name());
        assertEquals("PRI", columns.get(0).key());
        assertEquals(true, data.resultSet());
        assertEquals("1", data.rows().get(0).get(0));
    }

    private static DatabaseConnectionService.DatabaseConnectionCommand command(
            String name,
            DatabaseType type,
            String host,
            int port,
            String password) {
        return new DatabaseConnectionService.DatabaseConnectionCommand(
                name,
                type,
                host,
                port,
                "analytics",
                "reader",
                password,
                false);
    }

    private static final class PrefixCipher implements CredentialCipher {

        @Override
        public String encrypt(String rawValue) {
            return "enc:" + rawValue;
        }

        @Override
        public String decrypt(String encryptedValue) {
            return encryptedValue.substring("enc:".length());
        }
    }

    private static final class InMemoryRepository
            implements DatabaseConnectionRepository {

        private DatabaseConnection connection;

        @Override
        public List<DatabaseConnection> findByOwnerAndWorkspace(
                long ownerId,
                String workspaceId) {
            return connection == null
                    || connection.getOwnerId() != ownerId
                    || !connection.getWorkspaceId().equals(workspaceId)
                    ? List.of()
                    : List.of(connection);
        }

        @Override
        public Optional<DatabaseConnection> findByIdAndOwner(
                long id,
                long ownerId) {
            return connection != null
                    && connection.getId() == id
                    && connection.getOwnerId() == ownerId
                    ? Optional.of(connection)
                    : Optional.empty();
        }

        @Override
        public DatabaseConnection save(DatabaseConnection next) {
            connection = DatabaseConnection.restore(
                    next.getId() == null ? 1L : next.getId(),
                    next.getOwnerId(),
                    next.getWorkspaceId(),
                    next.getName(),
                    next.getType(),
                    next.getHost(),
                    next.getPort(),
                    next.getDatabaseName(),
                    next.getUsername(),
                    next.getPasswordCiphertext(),
                    next.isSslEnabled(),
                    next.getCreatedAt(),
                    next.getUpdatedAt());
            return connection;
        }

        @Override
        public void deleteByIdAndOwner(long id, long ownerId) {
            if (connection != null
                    && connection.getId() == id
                    && connection.getOwnerId() == ownerId) {
                connection = null;
            }
        }
    }

    private static final class CapturingQueryGateway
            implements DatabaseQueryGateway {

        private ConnectionSpec lastSpec;
        private String lastSql;

        @Override
        public ConnectionTest test(ConnectionSpec connection) {
            return new ConnectionTest(true, "ok", 1);
        }

        @Override
        public QueryResult execute(
                ConnectionSpec connection,
                String sql,
                int maxRows) {
            lastSpec = connection;
            lastSql = sql;
            if (sql.contains("information_schema.SCHEMATA")) {
                return new QueryResult(
                        true,
                        List.of(new Column("SCHEMA_NAME", "VARCHAR")),
                        new ArrayList<>(List.of(
                                new ArrayList<>(List.of("analytics")))),
                        0,
                        false,
                        1);
            }
            if (sql.contains("information_schema.TABLES")) {
                return new QueryResult(
                        true,
                        List.of(
                                new Column("TABLE_NAME", "VARCHAR"),
                                new Column("TABLE_TYPE", "VARCHAR")),
                        new ArrayList<>(List.of(
                                new ArrayList<>(List.of(
                                        "auth_user",
                                        "BASE TABLE")))),
                        0,
                        false,
                        1);
            }
            if (sql.contains("information_schema.COLUMNS")) {
                return new QueryResult(
                        true,
                        List.of(
                                new Column("COLUMN_NAME", "VARCHAR"),
                                new Column("COLUMN_TYPE", "VARCHAR"),
                                new Column("IS_NULLABLE", "VARCHAR"),
                                new Column("COLUMN_KEY", "VARCHAR"),
                                new Column("COLUMN_DEFAULT", "VARCHAR"),
                                new Column("EXTRA", "VARCHAR")),
                        new ArrayList<>(List.of(
                                new ArrayList<>(List.of(
                                        "id",
                                        "bigint",
                                        "NO",
                                        "PRI",
                                        "",
                                        "auto_increment")))),
                        0,
                        false,
                        1);
            }
            return new QueryResult(
                    true,
                    List.of(new Column("value", "UInt8")),
                    new ArrayList<>(List.of(new ArrayList<>(List.of("1")))),
                    0,
                    false,
                    1);
        }
    }
}
