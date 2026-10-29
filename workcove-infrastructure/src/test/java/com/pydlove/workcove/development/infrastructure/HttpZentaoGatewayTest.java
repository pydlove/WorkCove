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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpZentaoGatewayTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> resolveBody = new AtomicReference<>();
    private final AtomicReference<String> confirmBody = new AtomicReference<>();
    private final AtomicReference<String> commentBody = new AtomicReference<>();
    private final AtomicReference<byte[]> uploadedFileBody =
            new AtomicReference<>();
    private final AtomicReference<String> createBuildBody = new AtomicReference<>();
    private final AtomicReference<String> createTestTaskBody =
            new AtomicReference<>();
    private final AtomicBoolean bugConfirmed = new AtomicBoolean(false);
    private final AtomicBoolean bugCommented = new AtomicBoolean(false);
    private final AtomicInteger commentRequests = new AtomicInteger();

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
        server.createContext("/zentao/api.php/v2/files", exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
            assertThat(exchange.getRequestHeaders().getFirst("token"))
                    .isEqualTo("test-token");
            assertThat(exchange.getRequestHeaders().getFirst("Content-Type"))
                    .contains("multipart/form-data");
            uploadedFileBody.set(
                    exchange.getRequestBody().readAllBytes());
            respond(exchange, """
                    {
                      "status":"success",
                      "id":"9001",
                      "url":"/zentao/file-read-1.png"
                    }
                    """);
        });
        server.createContext("/zentao/api.php/v2/products", exchange ->
                respond(exchange, """
                        {
                          "status":"success",
                          "products":[{"id":"1","name":"AI 安全网关"}]
                        }
                        """));
        server.createContext(
                "/zentao/api.php/v2/products/1",
                exchange -> respond(exchange, """
                        {
                          "status":"success",
                          "product":{"id":"1","name":"AI 安全网关"}
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
                          "confirmed":1,
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
        server.createContext("/zentao/api.php/v2/projects/1448/builds", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("token"))
                    .isEqualTo("test-token");
            assertThat(exchange.getRequestURI().getQuery())
                    .contains("projectID=1448")
                    .contains("recPerPage=1000")
                    .contains("pageID=1");
            respond(exchange, """
                    {
                      "status":"success",
                      "builds":[{
                        "id":"501",
                        "product":{"id":"1","name":"AI 安全网关"},
                        "execution":"1448",
                        "name":"v3.0.2-R100",
                        "status":"success",
                        "builder":"panyong",
                        "date":"2026-10-29 09:00:00",
                        "desc":"研发构建",
                        "scmRevision":"abc123"
                      }]
                    }
                    """);
        });
        server.createContext(
                "/zentao/api.php/v2/projects/1448/executions",
                exchange -> respond(exchange, """
                        {
                          "status":"success",
                          "executions":[
                            {
                              "id":"1455",
                              "project":"1448",
                              "product":"1",
                              "name":"【主线】AISG_V3.0.2_R100",
                              "status":"doing"
                            }
                          ]
                        }
                        """));
        server.createContext(
                "/zentao/api.php/v2/products/1/systems",
                exchange -> respond(exchange, """
                        {
                          "status":"success",
                          "systems":[
                            {"id":"127","product":"1","name":"大模型应用防火墙"}
                          ]
                        }
                        """));
        server.createContext(
                "/zentao/api.php/v2/users",
                exchange -> respond(exchange, """
                        {
                          "status":"success",
                          "users":[
                            {"account":"panyong","realname":"潘勇"},
                            {"account":"qa","realname":"测试"}
                          ]
                        }
                        """));
        server.createContext(
                "/zentao/api.php/v2/products/1/testreports",
                exchange -> respond(exchange, """
                        {
                          "status":"success",
                          "testreports":[
                            {"id":"42","title":"V3.0.2 回归报告"}
                          ]
                        }
                        """));
        server.createContext("/zentao/api.php/v2/builds", exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
            String body = new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8);
            createBuildBody.set(body);
            if (body.contains("\"name\":\"__missing__\"")) {
                respond(exchange, """
                        {
                          "result":"fail",
                          "message":{"name":["『名称』不能为空。"]}
                        }
                        """);
                return;
            }
            respond(exchange, """
                    {
                      "status":"success",
                      "id":"900",
                      "build":{"name":"V3.0.2_R100B003"}
                    }
                    """);
        });
        server.createContext("/zentao/api.php/v2/testtasks", exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
            createTestTaskBody.set(new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            respond(exchange, """
                    {
                      "status":"success",
                      "id":"901",
                      "testtask":{"name":"系统测试"}
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
        server.createContext("/zentao/api.php/v2/actions/comment", exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
            assertThat(exchange.getRequestHeaders().getFirst("token"))
                    .isEqualTo("test-token");
            commentRequests.incrementAndGet();
            commentBody.set(new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            bugCommented.set(true);
            respond(exchange, """
                    {"status":"success"}
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
        server.createContext("/zentao/api.php/v2/bugs/1028/confirm", exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("PUT");
            confirmBody.set(new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            bugConfirmed.set(true);
            respond(exchange, """
                    {"status":"success"}
                    """);
        });
        server.createContext("/zentao/api.php/v2/bugs/1028", exchange -> {
            String confirmed = bugConfirmed.get() ? "1" : "0";
            String actions = bugCommented.get()
                    ? """
                      [{
                        "action":"commented",
                        "comment":"[WorkCove-Codex-Test:test-run-1]"
                      }]
                      """
                    : "[]";
            respond(exchange, """
                        {
                          "status":"success",
                          "bug":{
                            "id":1028,
                            "pri":2,
                            "type":"codeerror",
                            "status":"active",
                            "deadline":"",
                            "confirmed":%s
                          },
                          "actions":%s
                        }
                        """.formatted(confirmed, actions));
        });
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
        ZentaoGateway.Product productDetail =
                gateway.getProduct(baseUrl, token, "1");
        List<ZentaoGateway.Project> projects =
                gateway.listProjects(baseUrl, token);
        List<ZentaoGateway.RemoteBug> bugs =
                gateway.listProjectBugs(baseUrl, token, "1448", "yong");
        List<ZentaoGateway.RemoteBuild> builds =
                gateway.listProjectBuilds(baseUrl, token, "1448");
        List<ZentaoGateway.RemoteExecution> executions =
                gateway.listProjectExecutions(baseUrl, token, "1448");
        List<ZentaoGateway.RemoteSystem> systems =
                gateway.listProductSystems(baseUrl, token, "1");
        List<ZentaoGateway.RemoteUser> users =
                gateway.listUsers(baseUrl, token);
        List<ZentaoGateway.RemoteTestReport> reports =
                gateway.listProductTestReports(baseUrl, token, "1");
        ZentaoGateway.MediaFile media = gateway.fetchMedia(
                baseUrl,
                token,
                "file-read-1.png");
        ZentaoGateway.CreatedBuild createdBuild = gateway.createBuild(
                baseUrl,
                token,
                new ZentaoGateway.CreateBuild(
                        "1448",
                        "1455",
                        "1",
                        "127",
                        "V3.0.2_R100B003",
                        "panyong",
                        "2026-10-29",
                        "https://example.com/build.tar.gz"));
        ZentaoGateway.CreatedTestTask createdTestTask =
                gateway.createTestTask(
                        baseUrl,
                        token,
                        new ZentaoGateway.CreateTestTask(
                                "1448",
                                "1455",
                                "1",
                                "501",
                                List.of("system"),
                                "qa",
                                "qa,pm",
                                "wait",
                                "2026-10-29",
                                "2026-11-05",
                                "42",
                                "系统测试"));

        gateway.resolveBug(
                baseUrl,
                token,
                "1024",
                "yong",
                "fixed",
                "解决方案：已修复\n原因：竞态条件\n\n由 8小时工作台回写。");
        gateway.confirmBug(
                baseUrl,
                token,
                "1028",
                "yong",
                "由 8小时工作台确认。");

        assertThat(token).isEqualTo("test-token");
        assertThat(products).containsExactly(
                new ZentaoGateway.Product("1", "AI 安全网关"));
        assertThat(productDetail).isEqualTo(
                new ZentaoGateway.Product("1", "AI 安全网关"));
        assertThat(projects).containsExactly(
                new ZentaoGateway.Project("1448", "AI 安全网关", "doing"));
        assertThat(bugs).hasSize(1);
        assertThat(bugs.get(0).id()).isEqualTo("1024");
        assertThat(bugs.get(0).projectId()).isEqualTo("1448");
        assertThat(bugs.get(0).productId()).isEqualTo("1");
        assertThat(bugs.get(0).confirmed()).isTrue();
        assertThat(bugs.get(0).assignedTo()).isEqualTo("yong");
        assertThat(builds).hasSize(1);
        assertThat(builds.get(0).name()).isEqualTo("v3.0.2-R100");
        assertThat(builds.get(0).productId()).isEqualTo("1");
        assertThat(builds.get(0).scmRevision()).isEqualTo("abc123");
        assertThat(executions).containsExactly(
                new ZentaoGateway.RemoteExecution(
                        "1455",
                        "1448",
                        "1",
                        "【主线】AISG_V3.0.2_R100",
                        "doing"));
        assertThat(systems).containsExactly(
                new ZentaoGateway.RemoteSystem(
                        "127",
                        "1",
                        "大模型应用防火墙"));
        assertThat(users).hasSize(2);
        assertThat(reports).containsExactly(
                new ZentaoGateway.RemoteTestReport(
                        "42",
                        "V3.0.2 回归报告"));
        assertThat(createdBuild.id()).isEqualTo("900");
        assertThat(createdBuild.name()).isEqualTo("V3.0.2_R100B003");
        assertThat(createdTestTask.id()).isEqualTo("901");
        assertThat(createdTestTask.name()).isEqualTo("系统测试");
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
        JsonNode confirm = objectMapper.readTree(confirmBody.get());
        assertThat(confirm.path("assignedTo").asText()).isEqualTo("yong");
        assertThat(confirm.path("pri").asInt()).isEqualTo(2);
        assertThat(confirm.path("type").asText()).isEqualTo("codeerror");
        assertThat(confirm.path("comment").asText())
                .contains("由 8小时工作台确认");
        JsonNode buildBody = objectMapper.readTree(createBuildBody.get());
        assertThat(buildBody.path("executionID").asText())
                .isEqualTo("1455");
        assertThat(buildBody.path("product").asText()).isEqualTo("1");
        assertThat(buildBody.path("system").asText()).isEqualTo("127");
        assertThat(buildBody.path("filePath").asText())
                .isEqualTo("https://example.com/build.tar.gz");
        JsonNode testTaskBody =
                objectMapper.readTree(createTestTaskBody.get());
        assertThat(testTaskBody.path("project").asText())
                .isEqualTo("1448");
        assertThat(testTaskBody.path("productID").asText())
                .isEqualTo("1");
        assertThat(testTaskBody.path("members").asText())
                .isEqualTo("qa,pm");
        assertThat(testTaskBody.path("type").get(0).asText())
                .isEqualTo("system");
        assertThat(testTaskBody.path("testreport").asText())
                .isEqualTo("42");
    }

    @Test
    void appendsCodexTestCommentsIdempotently() throws Exception {
        HttpZentaoGateway gateway = new HttpZentaoGateway(objectMapper);
        String token = gateway.authenticate(
                baseUrl + "/project-bug-1448-0-all-assignedTo_asc",
                "yong",
                "secret");
        String marker = "[WorkCove-Codex-Test:test-run-1]";
        String comment = "【Codex 自动化测试结果】\n测试结论：通过\n"
                + marker;

        List<ZentaoGateway.UploadedBugFile> uploaded =
                gateway.uploadBugFiles(
                        baseUrl,
                        token,
                        "1028",
                        List.of(new ZentaoGateway.UploadFile(
                                "bug-fixed.png",
                                "image/png",
                                new byte[] {1, 2, 3})));
        boolean first = gateway.commentBug(
                baseUrl,
                token,
                "1028",
                comment,
                marker);
        boolean second = gateway.commentBug(
                baseUrl,
                token,
                "1028",
                comment,
                marker);

        assertThat(first).isFalse();
        assertThat(second).isTrue();
        assertThat(uploaded).hasSize(1);
        assertThat(uploaded.get(0).url())
                .isEqualTo(baseUrl + "/file-read-1.png");
        String multipart = new String(
                uploadedFileBody.get(),
                StandardCharsets.UTF_8);
        assertThat(multipart)
                .contains("name=\"objectType\"")
                .contains("bug")
                .contains("name=\"objectID\"")
                .contains("1028")
                .contains("filename=\"bug-fixed.png\"");
        assertThat(commentRequests).hasValue(1);
        JsonNode body = objectMapper.readTree(commentBody.get());
        assertThat(body.path("objectType").asText()).isEqualTo("bug");
        assertThat(body.path("objectID").asText()).isEqualTo("1028");
        assertThat(body.path("actioncomment").asText())
                .contains("测试结论：通过")
                .contains(marker);
        assertThat(body.has("title")).isFalse();
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
    void surfacesValidationErrorsReturnedInResultField() {
        HttpZentaoGateway gateway = new HttpZentaoGateway(objectMapper);
        String token = gateway.authenticate(
                baseUrl + "/project-bug-1448-0-all-assignedTo_asc",
                "yong",
                "secret");

        assertThatThrownBy(() -> gateway.createBuild(
                baseUrl,
                token,
                new ZentaoGateway.CreateBuild(
                        "1448",
                        "1455",
                        "1",
                        "127",
                        "__missing__",
                        "panyong",
                        "2026-10-29",
                        "")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("名称")
                .hasMessageContaining("不能为空");
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
