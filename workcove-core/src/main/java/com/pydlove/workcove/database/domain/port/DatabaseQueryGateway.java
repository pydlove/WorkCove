package com.pydlove.workcove.database.domain.port;

import com.pydlove.workcove.database.domain.model.DatabaseType;

import java.util.List;

public interface DatabaseQueryGateway {

    ConnectionTest test(ConnectionSpec connection);

    QueryResult execute(
            ConnectionSpec connection,
            String sql,
            int maxRows);

    record ConnectionSpec(
            DatabaseType type,
            String host,
            int port,
            String databaseName,
            String username,
            String password,
            boolean sslEnabled) {
    }

    record ConnectionTest(
            boolean success,
            String message,
            long elapsedMs) {
    }

    record Column(
            String name,
            String type) {
    }

    record QueryResult(
            boolean resultSet,
            List<Column> columns,
            List<List<String>> rows,
            long affectedRows,
            boolean truncated,
            long elapsedMs) {
    }
}
