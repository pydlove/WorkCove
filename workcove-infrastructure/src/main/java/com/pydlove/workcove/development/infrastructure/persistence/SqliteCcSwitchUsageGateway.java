package com.pydlove.workcove.development.infrastructure.persistence;

import com.pydlove.workcove.development.domain.port.CcSwitchUsageGateway;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Optional;

@Component
public class SqliteCcSwitchUsageGateway
        implements CcSwitchUsageGateway {

    private final Path databasePath;

    public SqliteCcSwitchUsageGateway() {
        this(Path.of(
                System.getProperty("user.home"),
                ".cc-switch",
                "cc-switch.db"));
    }

    SqliteCcSwitchUsageGateway(Path databasePath) {
        this.databasePath = databasePath;
    }

    @Override
    public Usage read(Long fromEpochSeconds) {
        if (!Files.isRegularFile(databasePath)) {
            return empty("未找到 CC Switch 数据库");
        }

        String where = fromEpochSeconds == null
                ? ""
                : """
                  WHERE (
                    CASE
                      WHEN created_at > 100000000000
                        THEN created_at / 1000
                      ELSE created_at
                    END
                  ) >= ?
                  """;
        String sql = """
                SELECT
                  COUNT(*) AS request_count,
                  COALESCE(SUM(
                    CASE
                      WHEN status_code >= 200 AND status_code < 400
                        THEN 1 ELSE 0
                    END
                  ), 0) AS success_count,
                  COALESCE(SUM(input_tokens), 0) AS input_tokens,
                  COALESCE(SUM(output_tokens), 0) AS output_tokens,
                  COALESCE(SUM(cache_read_tokens), 0) AS cache_read_tokens,
                  COALESCE(SUM(cache_creation_tokens), 0) AS cache_creation_tokens,
                  COALESCE(SUM(CAST(total_cost_usd AS REAL)), 0) AS total_cost_usd
                FROM proxy_request_logs
                """ + where;

        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + databasePath);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            try (var pragma = connection.createStatement()) {
                pragma.execute("PRAGMA query_only = ON");
                pragma.execute("PRAGMA busy_timeout = 3000");
            }
            if (fromEpochSeconds != null) {
                statement.setLong(1, fromEpochSeconds);
            }
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return empty("CC Switch 用量表没有返回数据");
                }
                long inputTokens = result.getLong("input_tokens");
                long outputTokens = result.getLong("output_tokens");
                long cacheReadTokens = result.getLong("cache_read_tokens");
                Double cacheHitRate = inputTokens > 0
                        ? Math.min(
                                100,
                                Math.max(
                                        0,
                                        (double) cacheReadTokens
                                                / inputTokens
                                                * 100))
                        : null;
                return new Usage(
                        true,
                        "",
                        result.getLong("request_count"),
                        result.getLong("success_count"),
                        inputTokens,
                        outputTokens,
                        cacheReadTokens,
                        result.getLong("cache_creation_tokens"),
                        inputTokens + outputTokens,
                        result.getDouble("total_cost_usd"),
                        cacheHitRate,
                        Instant.now());
            }
        } catch (Exception exception) {
            return empty(
                    "无法读取 CC Switch 数据库："
                            + Optional.ofNullable(exception.getMessage())
                            .orElse(exception.getClass().getSimpleName()));
        }
    }

    private Usage empty(String reason) {
        return new Usage(
                false,
                reason,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                null,
                Instant.now());
    }
}
