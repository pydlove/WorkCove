package com.pydlove.workcove.database.infrastructure.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pydlove.workcove.database.domain.model.DatabaseType;
import com.pydlove.workcove.database.domain.port.DatabaseQueryGateway;
import com.pydlove.workcove.shared.domain.DomainException;
import org.springframework.stereotype.Component;

import java.io.Reader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

@Component
public class DefaultDatabaseQueryGateway
        implements DatabaseQueryGateway {

    private static final int QUERY_TIMEOUT_SECONDS = 30;
    private static final int PREVIEW_ROWS = 500;
    private static final int MAX_CELL_LENGTH = 20_000;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper;

    public DefaultDatabaseQueryGateway(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ConnectionTest test(ConnectionSpec connection) {
        long startedAt = System.nanoTime();
        if (connection.type() == DatabaseType.MYSQL) {
            try (Connection jdbc = mysqlConnection(connection);
                 Statement statement = jdbc.createStatement()) {
                statement.setQueryTimeout(10);
                try (ResultSet result = statement.executeQuery("SELECT 1")) {
                    result.next();
                }
                return new ConnectionTest(
                        true,
                        "MySQL 连接成功",
                        elapsed(startedAt));
            } catch (SQLException exception) {
                throw queryError("MySQL 连接失败", exception);
            }
        }
        executeClickHouse(connection, "SELECT 1", 1);
        return new ConnectionTest(
                true,
                "ClickHouse 连接成功",
                elapsed(startedAt));
    }

    @Override
    public QueryResult execute(
            ConnectionSpec connection,
            String sql,
            int maxRows) {
        int safeRows = Math.max(1, Math.min(maxRows, PREVIEW_ROWS));
        long startedAt = System.nanoTime();
        if (connection.type() == DatabaseType.MYSQL) {
            return executeMysql(connection, sql, safeRows, startedAt);
        }
        return executeClickHouse(connection, sql, safeRows);
    }

    private QueryResult executeMysql(
            ConnectionSpec connection,
            String sql,
            int maxRows,
            long startedAt) {
        try (Connection jdbc = mysqlConnection(connection);
             Statement statement = jdbc.createStatement()) {
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            statement.setMaxRows(maxRows + 1);
            boolean hasResult = statement.execute(sql);
            if (!hasResult) {
                return new QueryResult(
                        false,
                        List.of(),
                        List.of(),
                        Math.max(0, statement.getUpdateCount()),
                        false,
                        elapsed(startedAt));
            }
            try (ResultSet result = statement.getResultSet()) {
                ResultSetMetaData metadata = result.getMetaData();
                List<Column> columns = new ArrayList<>();
                for (int index = 1; index <= metadata.getColumnCount(); index += 1) {
                    columns.add(new Column(
                            metadata.getColumnLabel(index),
                            metadata.getColumnTypeName(index)));
                }
                List<List<String>> rows = new ArrayList<>();
                boolean truncated = false;
                while (result.next()) {
                    if (rows.size() >= maxRows) {
                        truncated = true;
                        break;
                    }
                    List<String> row = new ArrayList<>(columns.size());
                    for (int index = 1; index <= columns.size(); index += 1) {
                        row.add(displayValue(result.getObject(index)));
                    }
                    rows.add(row);
                }
                return new QueryResult(
                        true,
                        columns,
                        rows,
                        0,
                        truncated,
                        elapsed(startedAt));
            }
        } catch (SQLException exception) {
            throw queryError("MySQL 执行失败", exception);
        }
    }

    private QueryResult executeClickHouse(
            ConnectionSpec connection,
            String sql,
            int maxRows) {
        long startedAt = System.nanoTime();
        URI uri = clickHouseUri(connection, maxRows);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(QUERY_TIMEOUT_SECONDS + 5))
                .header("Content-Type", "text/plain; charset=utf-8")
                .header("X-ClickHouse-User", connection.username())
                .header(
                        "X-ClickHouse-Key",
                        connection.password() == null
                                ? ""
                                : connection.password())
                .POST(HttpRequest.BodyPublishers.ofString(
                        sql,
                        StandardCharsets.UTF_8));
        try {
            HttpResponse<String> response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new DomainException(
                        "ClickHouse 执行失败：" + compact(response.body()));
            }
            String body = response.body() == null ? "" : response.body().trim();
            if (body.isEmpty()) {
                return new QueryResult(
                        false,
                        List.of(),
                        List.of(),
                        0,
                        false,
                        elapsed(startedAt));
            }
            JsonNode root = objectMapper.readTree(body);
            JsonNode meta = root.path("meta");
            JsonNode data = root.path("data");
            if (!meta.isArray()) {
                return new QueryResult(
                        false,
                        List.of(),
                        List.of(),
                        0,
                        false,
                        elapsed(startedAt));
            }
            List<Column> columns = new ArrayList<>();
            for (JsonNode item : meta) {
                columns.add(new Column(
                        item.path("name").asText(""),
                        item.path("type").asText("")));
            }
            List<List<String>> rows = new ArrayList<>();
            boolean truncated = false;
            if (data.isArray()) {
                for (JsonNode item : data) {
                    if (rows.size() >= maxRows) {
                        truncated = true;
                        break;
                    }
                    List<String> row = new ArrayList<>(columns.size());
                    for (Column column : columns) {
                        row.add(jsonDisplayValue(item.get(column.name())));
                    }
                    rows.add(row);
                }
            }
            return new QueryResult(
                    true,
                    columns,
                    rows,
                    0,
                    truncated,
                    elapsed(startedAt));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DomainException("ClickHouse 查询已中断", exception);
        } catch (DomainException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DomainException(
                    "ClickHouse 执行失败：" + compact(exception.getMessage()),
                    exception);
        }
    }

    private Connection mysqlConnection(ConnectionSpec connection)
            throws SQLException {
        String database = connection.databaseName() == null
                ? ""
                : connection.databaseName();
        String url = "jdbc:mysql://"
                + connection.host()
                + ":"
                + connection.port()
                + "/"
                + database
                + "?useSSL="
                + connection.sslEnabled()
                + "&allowPublicKeyRetrieval=true"
                + "&connectTimeout=10000"
                + "&socketTimeout=35000"
                + "&characterEncoding=UTF-8";
        Properties properties = new Properties();
        properties.setProperty("user", connection.username());
        properties.setProperty(
                "password",
                connection.password() == null ? "" : connection.password());
        DriverManager.setLoginTimeout(10);
        return DriverManager.getConnection(url, properties);
    }

    private URI clickHouseUri(ConnectionSpec connection, int maxRows) {
        String scheme = connection.sslEnabled() ? "https" : "http";
        String database = connection.databaseName() == null
                ? ""
                : connection.databaseName().trim();
        String databaseParameter = database.isEmpty()
                ? ""
                : "database="
                        + URLEncoder.encode(database, StandardCharsets.UTF_8)
                                .replace("+", "%20")
                        + "&";
        return URI.create(
                scheme
                        + "://"
                        + connection.host()
                        + ":"
                        + connection.port()
                        + "/?"
                        + databaseParameter
                        + "default_format=JSON"
                        + "&max_execution_time="
                        + QUERY_TIMEOUT_SECONDS
                        + "&max_result_rows="
                        + maxRows
                        + "&result_overflow_mode=break"
                        + "&max_result_bytes=10485760"
                        + "&wait_end_of_query=1");
    }

    private static String displayValue(Object value) {
        if (value == null) return null;
        try {
            if (value instanceof byte[] bytes) {
                return truncateCell(
                        Base64.getEncoder().encodeToString(bytes));
            }
            if (value instanceof Clob clob) {
                try (Reader reader = clob.getCharacterStream()) {
                    StringBuilder text = new StringBuilder();
                    char[] buffer = new char[2048];
                    int count;
                    while ((count = reader.read(buffer)) >= 0) {
                        text.append(buffer, 0, count);
                    }
                    return truncateCell(text.toString());
                }
            }
            if (value instanceof Blob blob) {
                return truncateCell(Base64.getEncoder().encodeToString(
                        blob.getBytes(
                                1,
                                (int) Math.min(blob.length(), 65_536))));
            }
        } catch (Exception ignored) {
            return truncateCell(String.valueOf(value));
        }
        return truncateCell(String.valueOf(value));
    }

    private static String jsonDisplayValue(JsonNode value) {
        if (value == null || value.isNull()) return null;
        if (value.isValueNode()) return truncateCell(value.asText());
        return truncateCell(value.toString());
    }

    private static String truncateCell(String value) {
        if (value == null || value.length() <= MAX_CELL_LENGTH) return value;
        return value.substring(0, MAX_CELL_LENGTH) + "…";
    }

    private static DomainException queryError(
            String prefix,
            SQLException exception) {
        String message = exception.getMessage();
        return new DomainException(
                prefix + "：" + compact(
                        message == null
                                ? exception.getClass().getSimpleName()
                                : message),
                exception);
    }

    private static String compact(String value) {
        if (value == null || value.isBlank()) return "未知错误";
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() > 1000
                ? normalized.substring(0, 1000)
                : normalized;
    }

    private static long elapsed(long startedAt) {
        return Math.max(
                0,
                Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
    }
}
