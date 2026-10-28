package com.pydlove.workcove.development.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pydlove.workcove.development.domain.port.ZentaoGateway;
import com.pydlove.workcove.shared.domain.DomainException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpZentaoGatewayTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> resolveBody = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/zentao/api.php/v2/users/login", exchange -> {
            String requestBody = new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8);
            if (requestBody.contains("\"invalid\"")) {
                respond(exchange, """
                        {
                          "status":"failed",
                          "reason":"登录失败，请检查您的用户名或密码是否填写正确。"
                        }
                        """);
                return;
            }
            exchange.getResponseHeaders().add(
                    "Set-Cookie",
                    "zentaosid=test-session; Path=/; HttpOnly");
            respond(exchange, """
                    {"status":"success","token":"test-token"}
                    """);
        });
        server.createContext("/zentao/file-read-1.png", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("token"))
                    .isEqualTo("test-token");
            assertThat(exchange.getRequestHeaders().getFirst("Cookie"))
                    .contains("zentaosid=test-session");
            respondBytes(
                    exchange,
                    new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47},
                    "image/png");
        });
        server.createContext("/zentao/api.php/v2/products", exchange ->
                respond(exchange, """
                        {
                          "status":"success",
                          "products":[{"id":"1","name":"AI 安全网关"}]
                        }
                        """));
        server.createContext("/zentao/api.php/v2/projects", exchange -> {
            assertThat(exchange.getRequestURI().getQuery())
                    .contains("browseType=undone");
            respond(exchange, """
                        {
                          "status":"success",
                          "projects":[
                            {"id":"1448","name":"AI 安全网关","status":"doing"},
                            {"id":"1450","name":"已关闭项目","status":"closed"}
                          ]
                        }
                        """);
        });
        server.createContext("/zentao/api.php/v2/projects/1448/bugs", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("token"))
                    .isEqualTo("test-token");
            respond(exchange, """
                    {
                      "status":"success",
                      "bugs":[
                        {
                          "id":"1024",
                          "product":"1",
                          "title":"策略同步后偶发白屏",
                          "severity":"2",
                          "pri":"1",
                          "status":"active",
                          "steps":"刷新页面后白屏",
                          "openedBy":"qa",
                          "openedDate":"2026-10-27 09:00:00",
                          "assignedTo":"yong"
                        },
                        {
                          "id":"1025",
                          "product":"1",
                          "title":"其他同事的 Bug",
                          "severity":"2",
                          "pri":"2",
                          "status":"active",
                          "steps":"无需同步",
                          "openedBy":"qa",
                          "openedDate":"2026-10-27 09:01:00",
                          "assignedTo":"other"
                        }
                      ]
                    }
                    """);
        });
        server.createContext("/zentao/api.php/v2/bugs/1024/resolve", exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("PUT");
            assertThat(exchange.getRequestHeaders().getFirst("token"))
                    .isEqualTo("test-token");
            resolveBody.set(new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            respond(exchange, """
                    {"status":"success"}
                    """);
        });
        server.createContext("/zentao/api.php/v2/bugs/1024", exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("GET");
            assertThat(exchange.getRequestHeaders().getFirst("token"))
                    .isEqualTo("test-token");
            respond(exchange, """
                    {
                      "status":"success",
                      "bug":{"id":1024,"status":"resolved"},
                      "actions":[
                        {
                          "action":"resolved",
                          "comment":"解决方案：已修复\\n原因：竞态条件\\n\\n由 8小时工作台回写。"
                        }
                      ]
                    }
                    """);
        });
        server.createContext("/zentao/api.php/v2/bugs/1026/resolve", exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("PUT");
            respond(exchange, """
                    {"status":"success"}
                    """);
        });
        server.createContext("/zentao/api.php/v2/bugs/1026", exchange ->
                respond(exchange, """
                        {
                          "status":"success",
                          "bug":{"id":1026,"status":"active"}
                        }
                        """));
        server.createContext("/zentao/api.php/v2/bugs/1027/resolve", exchange ->
                respond(exchange, """
                        {"status":"success"}
                        """));
        server.createContext("/zentao/api.php/v2/bugs/1027", exchange ->
                respond(exchange, """
                        {
                          "status":"success",
                          "bug":{"id":1027,"status":"resolved"},
                          "actions":[]
                        }
                        """));
        server.start();
        baseUrl = "http://127.0.0.1:"
                + server.getAddress().getPort()
                + "/zentao";
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void synchronizesAndResolvesBugsThroughApiV2() throws Exception {
        HttpZentaoGateway gateway = new HttpZentaoGateway(objectMapper);
        String token = gateway.authenticate(
                baseUrl + "/project-bug-1448-0-all-assignedTo_asc",
                "yong",
                "secret");
        List<ZentaoGateway.Product> products =
                gateway.listProducts(baseUrl, token);
        List<ZentaoGateway.Project> projects =
                gateway.listProjects(baseUrl, token);
        List<ZentaoGateway.RemoteBug> bugs =
                gateway.listProjectBugs(baseUrl, token, "1448", "yong");
        ZentaoGateway.MediaFile media = gateway.fetchMedia(
                baseUrl,
                token,
                "file-read-1.png");

        gateway.resolveBug(
                baseUrl,
                token,
                "1024",
                "yong",
                "fixed",
                "解决方案：已修复\n原因：竞态条件\n\n由 8小时工作台回写。");

        assertThat(token).isEqualTo("test-token");
        assertThat(products).containsExactly(
                new ZentaoGateway.Product("1", "AI 安全网关"));
        assertThat(projects).containsExactly(
                new ZentaoGateway.Project("1448", "AI 安全网关", "doing"));
        assertThat(bugs).hasSize(1);
        assertThat(bugs.get(0).id()).isEqualTo("1024");
        assertThat(bugs.get(0).projectId()).isEqualTo("1448");
        assertThat(bugs.get(0).productId()).isEqualTo("1");
        assertThat(bugs.get(0).assignedTo()).isEqualTo("yong");
        assertThat(media.contentType()).isEqualTo("image/png");
        assertThat(media.content()).containsExactly(
                (byte) 0x89,
                (byte) 0x50,
                (byte) 0x4e,
                (byte) 0x47);

        JsonNode body = objectMapper.readTree(resolveBody.get());
        assertThat(body.path("resolution").asText()).isEqualTo("fixed");
        assertThat(body.path("resolvedBuild").asText()).isEqualTo("trunk");
        assertThat(body.path("assignedTo").asText()).isEqualTo("yong");
        assertThat(body.path("comment").asText())
                .contains("解决方案")
                .contains("由 8小时工作台回写")
                .contains("竞态条件");
    }

    @Test
    void surfacesTheReasonReturnedByZentao() {
        HttpZentaoGateway gateway = new HttpZentaoGateway(objectMapper);

        assertThatThrownBy(() ->
                gateway.authenticate(baseUrl, "invalid", "wrong"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("登录失败")
                .hasMessageContaining("用户名或密码");
    }

    @Test
    void failsWhenZentaoDoesNotPersistResolution() {
        HttpZentaoGateway gateway = new HttpZentaoGateway(objectMapper);
        String token = gateway.authenticate(
                baseUrl + "/project-bug-1448-0-all-assignedTo_asc",
                "yong",
                "secret");

        assertThatThrownBy(() -> gateway.resolveBug(
                baseUrl,
                token,
                "1026",
                "yong",
                "fixed",
                "解决方案：已修复"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("禅道未确认本次解决操作");
    }

    @Test
    void failsWhenZentaoDoesNotPersistTheResolutionComment() {
        HttpZentaoGateway gateway = new HttpZentaoGateway(objectMapper);
        String token = gateway.authenticate(
                baseUrl + "/project-bug-1448-0-all-assignedTo_asc",
                "yong",
                "secret");

        assertThatThrownBy(() -> gateway.resolveBug(
                baseUrl,
                token,
                "1027",
                "yong",
                "fixed",
                "解决方案：已修复\n由 8小时工作台回写。"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("解决备注未写入历史记录");
    }

    private static void respond(HttpExchange exchange, String body)
            throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set(
                "Content-Type",
                "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }

    private static void respondBytes(
            HttpExchange exchange,
            byte[] payload,
            String contentType) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }
}
