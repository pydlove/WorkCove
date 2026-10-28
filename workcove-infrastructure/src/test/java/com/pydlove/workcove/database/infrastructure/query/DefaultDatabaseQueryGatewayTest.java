package com.pydlove.workcove.database.infrastructure.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pydlove.workcove.database.domain.model.DatabaseType;
import com.pydlove.workcove.database.domain.port.DatabaseQueryGateway;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultDatabaseQueryGatewayTest {

    @Test
    void readsClickHouseJsonResultAndForwardsCredentials() throws Exception {
        AtomicReference<String> receivedSql = new AtomicReference<>();
        AtomicReference<String> receivedUser = new AtomicReference<>();
        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0),
                0);
        server.createContext("/", exchange -> {
            receivedSql.set(new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            receivedUser.set(
                    exchange.getRequestHeaders().getFirst("X-ClickHouse-User"));
            byte[] body = """
                    {
                      "meta": [
                        {"name": "value", "type": "UInt8"},
                        {"name": "label", "type": "String"}
                      ],
                      "data": [
                        {"value": 1, "label": "ok"}
                      ],
                      "rows": 1
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(
                    "Content-Type",
                    "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            DefaultDatabaseQueryGateway gateway =
                    new DefaultDatabaseQueryGateway(new ObjectMapper());
            var connection = new DatabaseQueryGateway.ConnectionSpec(
                    DatabaseType.CLICKHOUSE,
                    "127.0.0.1",
                    server.getAddress().getPort(),
                    "default",
                    "reader",
                    "secret",
                    false);

            var result = gateway.execute(connection, "SELECT 1", 10);

            assertTrue(result.resultSet());
            assertEquals(2, result.columns().size());
            assertEquals("value", result.columns().get(0).name());
            assertEquals("1", result.rows().get(0).get(0));
            assertEquals("ok", result.rows().get(0).get(1));
            assertEquals("SELECT 1", receivedSql.get());
            assertEquals("reader", receivedUser.get());
        } finally {
            server.stop(0);
        }
    }
}
