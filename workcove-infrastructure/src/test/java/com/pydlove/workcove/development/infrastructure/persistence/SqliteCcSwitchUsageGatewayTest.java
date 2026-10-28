package com.pydlove.workcove.development.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteCcSwitchUsageGatewayTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void aggregatesCcSwitchRequestUsage() throws Exception {
        Path databasePath = temporaryDirectory.resolve("cc-switch.db");
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + databasePath);
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE proxy_request_logs (
                      created_at INTEGER NOT NULL,
                      status_code INTEGER NOT NULL,
                      input_tokens INTEGER NOT NULL,
                      output_tokens INTEGER NOT NULL,
                      cache_read_tokens INTEGER NOT NULL,
                      cache_creation_tokens INTEGER NOT NULL,
                      total_cost_usd TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    INSERT INTO proxy_request_logs VALUES
                      (2000, 200, 100, 20, 75, 0, '0.25'),
                      (1000, 500, 900, 30, 0, 0, '1.00')
                    """);
        }

        SqliteCcSwitchUsageGateway gateway =
                new SqliteCcSwitchUsageGateway(databasePath);
        var usage = gateway.read(1500L);

        assertTrue(usage.available());
        assertEquals(1, usage.requestCount());
        assertEquals(1, usage.successCount());
        assertEquals(100, usage.inputTokens());
        assertEquals(20, usage.outputTokens());
        assertEquals(75, usage.cacheReadTokens());
        assertEquals(120, usage.totalTokens());
        assertEquals(0.25, usage.costUsd(), 0.000001);
        assertEquals(75.0, usage.cacheHitRate(), 0.0001);
    }
}
