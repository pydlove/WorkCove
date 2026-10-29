package com.pydlove.workcove.database.application;

import com.pydlove.workcove.database.domain.model.DatabaseConnection;
import com.pydlove.workcove.database.domain.model.DatabaseType;
import com.pydlove.workcove.database.domain.port.DatabaseQueryGateway;
import com.pydlove.workcove.database.domain.repository.DatabaseConnectionRepository;
import com.pydlove.workcove.development.domain.port.CredentialCipher;
import com.pydlove.workcove.shared.domain.DomainException;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

public class DatabaseConnectionService {

    private static final int MAX_SQL_LENGTH = 100_000;
    private static final int MAX_ROWS = 500;

    private final DatabaseConnectionRepository repository;
    private final DatabaseQueryGateway queryGateway;
    private final CredentialCipher credentialCipher;
    private final Clock clock;

    public DatabaseConnectionService(
            DatabaseConnectionRepository repository,
            DatabaseQueryGateway queryGateway,
            CredentialCipher credentialCipher,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.queryGateway = Objects.requireNonNull(queryGateway);
        this.credentialCipher = Objects.requireNonNull(credentialCipher);
        this.clock = Objects.requireNonNull(clock);
    }

    public List<DatabaseConnectionView> list(
            long ownerId,
            String workspaceId) {
        return repository.findByOwnerAndWorkspace(ownerId, workspaceId)
                .stream()
                .map(DatabaseConnectionView::from)
                .toList();
    }

    public DatabaseConnectionView save(
            long ownerId,
            String workspaceId,
            Long id,
            DatabaseConnectionCommand command) {
        Objects.requireNonNull(command, "数据库连接");
        DatabaseConnection existing = id == null
                ? null
                : require(ownerId, workspaceId, id);
        if (existing != null
                && !existing.getWorkspaceId().equals(workspaceId)) {
            throw new DomainException("数据库连接不存在或无权访问");
        }
        String passwordCiphertext = existing == null
                ? credentialCipher.encrypt(
                        command.password() == null ? "" : command.password())
                : command.password() == null || command.password().isEmpty()
                        ? existing.getPasswordCiphertext()
                        : credentialCipher.encrypt(command.password());

        DatabaseConnection connection = existing == null
                ? DatabaseConnection.create(
                        ownerId,
                        workspaceId,
                        command.name(),
                        command.type(),
                        command.host(),
                        command.port(),
                        command.databaseName(),
                        command.username(),
                        passwordCiphertext,
                        command.sslEnabled(),
                        clock.instant())
                : existing.update(
                        command.name(),
                        command.type(),
                        command.host(),
                        command.port(),
                        command.databaseName(),
                        command.username(),
                        passwordCiphertext,
                        command.sslEnabled(),
                        clock.instant());
        return DatabaseConnectionView.from(repository.save(connection));
    }

    public void delete(long ownerId, String workspaceId, long id) {
        require(ownerId, workspaceId, id);
        repository.deleteByIdAndOwnerAndWorkspace(
                id,
                ownerId,
                workspaceId);
    }

    public DatabaseQueryGateway.ConnectionTest test(
            long ownerId,
            String workspaceId,
            Long id,
            DatabaseConnectionCommand command) {
        Objects.requireNonNull(command, "数据库连接");
        DatabaseConnection existing = id == null
                ? null
                : require(ownerId, workspaceId, id);
        if (existing != null
                && !existing.getWorkspaceId().equals(workspaceId)) {
            throw new DomainException("数据库连接不存在或无权访问");
        }
        DatabaseConnection.create(
                ownerId,
                workspaceId,
                command.name(),
                command.type(),
                command.host(),
                command.port(),
                command.databaseName(),
                command.username(),
                "validation",
                command.sslEnabled(),
                clock.instant());
        String password = command.password() == null
                || command.password().isEmpty()
                ? existing == null
                        ? ""
                        : credentialCipher.decrypt(
                                existing.getPasswordCiphertext())
                : command.password();
        return queryGateway.test(spec(
                command.type(),
                command.host(),
                command.port(),
                command.databaseName(),
                command.username(),
                password,
                command.sslEnabled()));
    }

    public DatabaseQueryGateway.QueryResult execute(
            long ownerId,
            String workspaceId,
            long id,
            String sql) {
        DatabaseConnection connection = require(ownerId, workspaceId, id);
        String normalizedSql = sql == null ? "" : sql.trim();
        if (normalizedSql.isEmpty()) {
            throw new DomainException("请输入 SQL");
        }
        if (normalizedSql.length() > MAX_SQL_LENGTH) {
            throw new DomainException("SQL 内容过长");
        }
        return queryGateway.execute(
                spec(connection),
                normalizedSql,
                MAX_ROWS);
    }

    public List<DatabaseTableView> tables(
            long ownerId,
            String workspaceId,
            long id) {
        return tables(ownerId, workspaceId, id, null);
    }

    public List<DatabaseTableView> tables(
            long ownerId,
            String workspaceId,
            long id,
            String databaseName) {
        DatabaseConnection connection = require(ownerId, workspaceId, id);
        String database = selectedDatabase(connection, databaseName);
        String sql = connection.getType() == DatabaseType.MYSQL
                ? mysqlTablesSql(database)
                : clickHouseTablesSql(database);
        DatabaseQueryGateway.QueryResult result = queryGateway.execute(
                spec(connection),
                sql,
                MAX_ROWS);
        return result.rows().stream()
                .map((row) -> new DatabaseTableView(
                        value(row, 0),
                        value(row, 1)))
                .toList();
    }

    public List<DatabaseColumnView> columns(
            long ownerId,
            String workspaceId,
            long id,
            String tableName) {
        return columns(ownerId, workspaceId, id, null, tableName);
    }

    public List<DatabaseColumnView> columns(
            long ownerId,
            String workspaceId,
            long id,
            String databaseName,
            String tableName) {
        DatabaseConnection connection = require(ownerId, workspaceId, id);
        String table = validateIdentifier(tableName, "表名");
        String database = selectedDatabase(connection, databaseName);
        String sql = connection.getType() == DatabaseType.MYSQL
                ? mysqlColumnsSql(database, table)
                : clickHouseColumnsSql(database, table);
        DatabaseQueryGateway.QueryResult result = queryGateway.execute(
                spec(connection),
                sql,
                MAX_ROWS);
        return result.rows().stream()
                .map((row) -> connection.getType() == DatabaseType.MYSQL
                        ? mysqlColumn(row)
                        : clickHouseColumn(row))
                .toList();
    }

    public DatabaseQueryGateway.QueryResult tableData(
            long ownerId,
            String workspaceId,
            long id,
            String tableName) {
        return tableData(ownerId, workspaceId, id, null, tableName);
    }

    public DatabaseQueryGateway.QueryResult tableData(
            long ownerId,
            String workspaceId,
            long id,
            String databaseName,
            String tableName) {
        DatabaseConnection connection = require(ownerId, workspaceId, id);
        String table = validateIdentifier(tableName, "表名");
        String database = selectedDatabase(connection, databaseName);
        String qualified = qualifiedTable(database, table);
        return queryGateway.execute(
                spec(connection),
                "SELECT * FROM " + qualified + " LIMIT 100",
                100);
    }

    public List<DatabaseSchemaView> databases(
            long ownerId,
            String workspaceId,
            long id) {
        DatabaseConnection connection = require(ownerId, workspaceId, id);
        String sql = connection.getType() == DatabaseType.MYSQL
                ? """
                  SELECT SCHEMA_NAME
                  FROM information_schema.SCHEMATA
                  ORDER BY SCHEMA_NAME
                  """
                : """
                  SELECT name
                  FROM system.databases
                  ORDER BY name
                  """;
        DatabaseQueryGateway.QueryResult result = queryGateway.execute(
                spec(connection),
                sql,
                MAX_ROWS);
        return result.rows().stream()
                .map((row) -> new DatabaseSchemaView(value(row, 0)))
                .toList();
    }

    private DatabaseConnection require(
            long ownerId,
            String workspaceId,
            long id) {
        return repository.findByIdAndOwnerAndWorkspace(
                        id,
                        ownerId,
                        workspaceId)
                .orElseThrow(() -> new DomainException(
                        "数据库连接不存在或无权访问"));
    }

    private DatabaseQueryGateway.ConnectionSpec spec(
            DatabaseType type,
            String host,
            int port,
            String databaseName,
            String username,
            String password,
            boolean sslEnabled) {
        return new DatabaseQueryGateway.ConnectionSpec(
                type,
                host,
                port,
                databaseName,
                username,
                password,
                sslEnabled);
    }

    private DatabaseQueryGateway.ConnectionSpec spec(
            DatabaseConnection connection) {
        return spec(
                connection.getType(),
                connection.getHost(),
                connection.getPort(),
                connection.getDatabaseName(),
                connection.getUsername(),
                credentialCipher.decrypt(
                        connection.getPasswordCiphertext()),
                connection.isSslEnabled());
    }

    private static String mysqlTablesSql(String database) {
        return """
                SELECT TABLE_NAME, TABLE_TYPE
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = %s
                ORDER BY TABLE_NAME
                """.formatted(sqlLiteral(database));
    }

    private static String clickHouseTablesSql(String database) {
        return """
                SELECT name, engine
                FROM system.tables
                WHERE database = %s
                ORDER BY name
                """.formatted(sqlLiteral(database));
    }

    private static String mysqlColumnsSql(
            String database,
            String table) {
        return """
                SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_KEY,
                       COLUMN_DEFAULT, EXTRA
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = %s AND TABLE_NAME = %s
                ORDER BY ORDINAL_POSITION
                """.formatted(
                sqlLiteral(database),
                sqlLiteral(table));
    }

    private static String clickHouseColumnsSql(
            String database,
            String table) {
        return """
                SELECT name, type, default_kind, default_expression, comment
                FROM system.columns
                WHERE database = %s AND table = %s
                ORDER BY position
                """.formatted(
                sqlLiteral(database),
                sqlLiteral(table));
    }

    private static String selectedDatabase(
            DatabaseConnection connection,
            String requested) {
        if (requested != null && !requested.isBlank()) {
            return validateIdentifier(requested, "数据库名");
        }
        return connection.getDatabaseName();
    }

    private static String sqlLiteral(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static String qualifiedTable(
            String database,
            String table) {
        String identifier = "`" + table.replace("`", "``") + "`";
        return database.isBlank()
                ? identifier
                : "`"
                        + database.replace("`", "``")
                        + "`."
                        + identifier;
    }

    private static String validateIdentifier(
            String value,
            String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new DomainException(label + "不能为空");
        }
        if (normalized.length() > 128
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new DomainException(label + "格式无效");
        }
        return normalized;
    }

    private static String value(List<String> row, int index) {
        return index < row.size() && row.get(index) != null
                ? row.get(index)
                : "";
    }

    private static DatabaseColumnView mysqlColumn(List<String> row) {
        return new DatabaseColumnView(
                value(row, 0),
                value(row, 1),
                "YES".equalsIgnoreCase(value(row, 2)),
                value(row, 3),
                value(row, 4),
                value(row, 5));
    }

    private static DatabaseColumnView clickHouseColumn(List<String> row) {
        String type = value(row, 1);
        String defaultKind = value(row, 2);
        String defaultExpression = value(row, 3);
        String comment = value(row, 4);
        return new DatabaseColumnView(
                value(row, 0),
                type,
                type.startsWith("Nullable("),
                "",
                defaultExpression,
                defaultKind,
                comment);
    }

    public record DatabaseConnectionCommand(
            String name,
            DatabaseType type,
            String host,
            int port,
            String databaseName,
            String username,
            String password,
            boolean sslEnabled) {
    }

    public record DatabaseConnectionView(
            long id,
            String workspaceId,
            String name,
            DatabaseType type,
            String host,
            int port,
            String databaseName,
            String username,
            boolean sslEnabled,
            java.time.Instant createdAt,
            java.time.Instant updatedAt) {

        public static DatabaseConnectionView from(
                DatabaseConnection connection) {
            return new DatabaseConnectionView(
                    connection.getId(),
                    connection.getWorkspaceId(),
                    connection.getName(),
                    connection.getType(),
                    connection.getHost(),
                    connection.getPort(),
                    connection.getDatabaseName(),
                    connection.getUsername(),
                    connection.isSslEnabled(),
                    connection.getCreatedAt(),
                    connection.getUpdatedAt());
        }
    }

    public record DatabaseTableView(
            String name,
            String type) {
    }

    public record DatabaseSchemaView(String name) {
    }

    public record DatabaseColumnView(
            String name,
            String type,
            boolean nullable,
            String key,
            String defaultValue,
            String extra,
            String comment) {

        public DatabaseColumnView(
                String name,
                String type,
                boolean nullable,
                String key,
                String defaultValue,
                String extra) {
            this(name, type, nullable, key, defaultValue, extra, "");
        }
    }
}
