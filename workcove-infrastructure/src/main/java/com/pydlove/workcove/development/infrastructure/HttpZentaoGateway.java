package com.pydlove.workcove.development.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pydlove.workcove.development.domain.model.ZentaoBaseUrl;
import com.pydlove.workcove.development.domain.port.ZentaoGateway;
import com.pydlove.workcove.shared.domain.DomainException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class HttpZentaoGateway implements ZentaoGateway {

    private static final int PAGE_SIZE = 1000;
    private static final int MAX_PAGES = 20;
    private static final int MAX_MEDIA_BYTES = 10 * 1024 * 1024;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Map<String, List<String>> mediaCookies =
            new ConcurrentHashMap<>();

    public HttpZentaoGateway(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String authenticate(
            String baseUrl,
            String username,
            String password) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("account", username);
        body.put("password", password);
        HttpResponse<String> httpResponse = send(
                "POST",
                apiBase(baseUrl) + "/users/login",
                null,
                body,
                "application/json");
        JsonNode response = parseResponse(httpResponse);
        String token = response.path("token").asText("").trim();
        if (token.isEmpty()) {
            throw new DomainException("禅道登录成功但没有返回 Token");
        }
        List<String> cookies = httpResponse.headers().allValues("Set-Cookie");
        if (!cookies.isEmpty()) {
            mediaCookies.put(token, List.copyOf(cookies));
        }
        return token;
    }

    @Override
    public List<Project> listProjects(String baseUrl, String token) {
        List<Project> projects = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            JsonNode root = request(
                    "GET",
                    apiBase(baseUrl) + "/projects"
                            + "?browseType=undone&recPerPage=" + PAGE_SIZE
                            + "&pageID=" + page,
                    token,
                    null);
            JsonNode items = root.path("projects");
            if (!items.isArray() || items.isEmpty()) break;
            for (JsonNode item : items) {
                String id = item.path("id").asText("").trim();
                String status = item.path("status").asText("");
                if (id.isEmpty()
                        || "1".equals(item.path("deleted").asText("0"))
                        || "1".equals(item.path("isTpl").asText("0"))
                        || "closed".equalsIgnoreCase(status)) {
                    continue;
                }
                projects.add(new Project(
                        id,
                        item.path("name").asText("项目 " + id),
                        status));
            }
            if (items.size() < PAGE_SIZE) break;
        }
        return projects;
    }

    @Override
    public List<Product> listProducts(String baseUrl, String token) {
        List<Product> products = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            JsonNode root = request(
                    "GET",
                    apiBase(baseUrl) + "/products"
                            + "?browseType=noclosed&recPerPage=" + PAGE_SIZE
                            + "&pageID=" + page,
                    token,
                    null);
            JsonNode items = root.path("products");
            if (!items.isArray() || items.isEmpty()) break;
            for (JsonNode item : items) {
                String id = item.path("id").asText("").trim();
                if (id.isEmpty()) continue;
                products.add(new Product(
                        id,
                        item.path("name").asText("产品 " + id)));
            }
            if (items.size() < PAGE_SIZE) break;
        }
        return products;
    }

    @Override
    public List<RemoteBug> listProjectBugs(
            String baseUrl,
            String token,
            String projectId,
            String assignedTo) {
        List<RemoteBug> bugs = new ArrayList<>();
        String expectedAssignee = assignedTo == null ? "" : assignedTo.trim();
        for (int page = 1; page <= MAX_PAGES; page++) {
            JsonNode root = request(
                    "GET",
                    apiBase(baseUrl) + "/projects/" + projectId + "/bugs"
                            + "?browseType=all&recPerPage=" + PAGE_SIZE
                            + "&pageID=" + page,
                    token,
                    null);
            JsonNode items = root.path("bugs");
            if (!items.isArray() || items.isEmpty()) break;
            for (JsonNode item : items) {
                String id = item.path("id").asText("").trim();
                if (id.isEmpty()) continue;
                String assignee = text(item, "assignedTo");
                if (!expectedAssignee.isEmpty()
                        && !expectedAssignee.equalsIgnoreCase(assignee)) {
                    continue;
                }
                bugs.add(new RemoteBug(
                        id,
                        projectId,
                        "",
                        text(item, "product"),
                        text(item, "title"),
                        text(item, "severity"),
                        text(item, "pri"),
                        text(item, "status"),
                        text(item, "steps"),
                        text(item, "openedBy"),
                        text(item, "openedDate"),
                        assignee));
            }
            if (items.size() < PAGE_SIZE) break;
        }
        return bugs;
    }

    @Override
    public void resolveBug(
            String baseUrl,
            String token,
            String remoteBugId,
            String assignedTo,
            String resolution,
            String comment) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("resolution", resolution);
        body.put("resolvedBuild", "trunk");
        body.put("assignedTo", assignedTo);
        body.put("comment", comment);
        request(
                "PUT",
                apiBase(baseUrl) + "/bugs/" + remoteBugId + "/resolve",
                token,
                body);
        JsonNode refreshed = request(
                "GET",
                apiBase(baseUrl) + "/bugs/" + remoteBugId,
                token,
                null);
        String status = refreshed.path("bug").path("status").asText("");
        if (!"resolved".equalsIgnoreCase(status)
                && !"closed".equalsIgnoreCase(status)) {
            throw new DomainException(
                    "禅道未确认本次解决操作，请检查解决版本或字段配置");
        }
        boolean commentPersisted = false;
        for (JsonNode action : refreshed.path("actions")) {
            if ("resolved".equalsIgnoreCase(
                    action.path("action").asText(""))
                    && action.path("comment").asText("")
                    .contains("由 8小时工作台回写")) {
                commentPersisted = true;
                break;
            }
        }
        if (!commentPersisted) {
            throw new DomainException(
                    "禅道已变更 Bug 状态，但解决备注未写入历史记录");
        }
    }

    @Override
    public MediaFile fetchMedia(
            String baseUrl,
            String token,
            String source) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(ZentaoBaseUrl.resolveAsset(baseUrl, source))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "image/*");
        if (token != null && !token.isBlank()) {
            builder.header("token", token);
            String cookie = cookieHeader(mediaCookies.get(token));
            if (!cookie.isBlank()) {
                builder.header("Cookie", cookie);
            }
        }

        HttpResponse<byte[]> response;
        try {
            response = httpClient.send(
                    builder.method(
                            "GET",
                            HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("禅道附件请求已中断", exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw new DomainException("无法读取禅道附件");
        }
        if (response.statusCode() != 200) {
            throw new DomainException("禅道附件需要重新登录或无权访问");
        }
        if (response.body().length == 0
                || response.body().length > MAX_MEDIA_BYTES) {
            throw new DomainException("禅道附件为空或超过 10MB");
        }
        String contentType = response.headers()
                .firstValue("Content-Type")
                .orElse("")
                .split(";", 2)[0]
                .trim()
                .toLowerCase();
        if (!contentType.startsWith("image/")
                && !"application/octet-stream".equals(contentType)) {
            throw new DomainException("禅道附件不是可预览的图片");
        }
        if ("application/octet-stream".equals(contentType)) {
            contentType = inferImageType(source);
        }
        return new MediaFile(
                response.body(),
                contentType);
    }

    private JsonNode request(
            String method,
            String url,
            String token,
            JsonNode body) {
        return parseResponse(send(
                method,
                url,
                token,
                body,
                "application/json"));
    }

    private HttpResponse<String> send(
            String method,
            String url,
            String token,
            JsonNode body,
            String accept) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", accept);
        if (token != null && !token.isBlank()) {
            builder.header("token", token);
        }
        if (body != null) {
            builder.header("Content-Type", "application/json");
            builder.method(
                    method,
                    HttpRequest.BodyPublishers.ofString(
                            body.toString(),
                            StandardCharsets.UTF_8));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }

        try {
            return httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("禅道请求已中断", exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw new DomainException("无法连接禅道，请检查地址和网络");
        }
    }

    private JsonNode parseResponse(HttpResponse<String> response) {
        JsonNode root;
        try {
            root = objectMapper.readTree(response.body());
        } catch (IOException exception) {
            throw new DomainException(
                    response.statusCode() >= 400
                            ? "禅道接口返回 HTTP " + response.statusCode()
                            : "禅道接口返回了无法识别的数据");
        }
        String status = root.path("status").asText("");
        if (response.statusCode() >= 400
                || (!status.isEmpty() && !"success".equalsIgnoreCase(status))) {
            String message = text(root, "reason");
            if (message.isEmpty()) message = text(root, "message");
            if (message.isEmpty()) message = text(root, "error");
            throw new DomainException(message.isEmpty()
                    ? "禅道接口请求失败"
                    : message);
        }
        return root;
    }

    private static String cookieHeader(List<String> cookies) {
        if (cookies == null || cookies.isEmpty()) return "";
        List<String> pairs = new ArrayList<>();
        for (String cookie : cookies) {
            if (cookie == null || cookie.isBlank()) continue;
            String pair = cookie.split(";", 2)[0].trim();
            if (!pair.isEmpty()) pairs.add(pair);
        }
        return String.join("; ", pairs);
    }

    private static String inferImageType(String source) {
        String value = source.toLowerCase();
        if (value.endsWith(".png")) return "image/png";
        if (value.endsWith(".jpg") || value.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (value.endsWith(".gif")) return "image/gif";
        if (value.endsWith(".webp")) return "image/webp";
        if (value.endsWith(".svg")) return "image/svg+xml";
        throw new DomainException("禅道附件不是可预览的图片");
    }

    private String apiBase(String rawBaseUrl) {
        return ZentaoBaseUrl.normalize(rawBaseUrl) + "/api.php/v2";
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }
}
