package com.pydlove.workcove.development.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pydlove.workcove.development.domain.model.ZentaoBaseUrl;
import com.pydlove.workcove.development.domain.port.ZentaoGateway;
import com.pydlove.workcove.shared.domain.DomainException;

import java.io.ByteArrayOutputStream;
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
import java.util.UUID;
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
    public Product getProduct(
            String baseUrl,
            String token,
            String productId) {
        JsonNode root = request(
                "GET",
                apiBase(baseUrl) + "/products/" + productId,
                token,
                null);
        JsonNode product = root.path("product");
        String id = text(product, "id");
        if (id.isEmpty()) id = productId;
        String name = text(product, "name");
        if (name.isEmpty()) name = "产品 " + id;
        return new Product(id, name);
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
                        booleanValue(item, "confirmed"),
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
    public List<RemoteBuild> listProjectBuilds(
            String baseUrl,
            String token,
            String projectId) {
        List<RemoteBuild> builds = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            JsonNode root = request(
                    "GET",
                    apiBase(baseUrl) + "/projects/" + projectId + "/builds"
                            + "?projectID=" + projectId
                            + "&recPerPage=" + PAGE_SIZE
                            + "&pageID=" + page,
                    token,
                    null);
            JsonNode items = root.path("builds");
            if (!items.isArray()) items = root.path("data");
            if (!items.isArray() || items.isEmpty()) break;
            for (JsonNode item : items) {
                String id = text(item, "id");
                if (id.isEmpty()) continue;
                JsonNode product = item.path("product");
                String productId = product.isObject()
                        ? text(product, "id")
                        : product.asText("");
                String productName = product.isObject()
                        ? text(product, "name")
                        : product.asText("");
                String execution = text(item, "execution");
                if (execution.isEmpty()) execution = text(item, "project");
                String name = firstText(item, "name", "title");
                if (name.isEmpty()) name = "构建 " + id;
                builds.add(new RemoteBuild(
                        id,
                        projectId,
                        productId,
                        execution,
                        name,
                        firstText(item, "status", "result"),
                        firstText(item, "builder", "createdBy", "openedBy"),
                        firstText(item, "date", "createdDate", "createdAt"),
                        firstText(item, "desc", "description"),
                        firstText(
                                item,
                                "scmRevision",
                                "revision",
                                "scmPath")));
            }
            if (items.size() < PAGE_SIZE) break;
        }
        return builds;
    }

    @Override
    public List<RemoteExecution> listProjectExecutions(
            String baseUrl,
            String token,
            String projectId) {
        List<RemoteExecution> executions = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            JsonNode root = request(
                    "GET",
                    apiBase(baseUrl) + "/projects/" + projectId
                            + "/executions?recPerPage=" + PAGE_SIZE
                            + "&pageID=" + page,
                    token,
                    null);
            JsonNode items = firstArray(root, "executions", "data");
            if (items == null || items.isEmpty()) break;
            for (JsonNode item : items) {
                String id = text(item, "id");
                if (id.isEmpty()) continue;
                JsonNode project = item.path("project");
                String itemProjectId = project.isObject()
                        ? text(project, "id")
                        : firstText(item, "project", "projectID");
                if (itemProjectId.isEmpty()) itemProjectId = projectId;
                JsonNode product = item.path("product");
                String itemProductId = product.isObject()
                        ? text(product, "id")
                        : firstText(item, "product", "productID");
                executions.add(new RemoteExecution(
                        id,
                        itemProjectId,
                        itemProductId,
                        firstText(item, "name", "title"),
                        text(item, "status")));
            }
            if (items.size() < PAGE_SIZE) break;
        }
        return executions;
    }

    @Override
    public List<RemoteSystem> listProductSystems(
            String baseUrl,
            String token,
            String productId) {
        List<RemoteSystem> systems = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            JsonNode root = request(
                    "GET",
                    apiBase(baseUrl) + "/products/" + productId
                            + "/systems?recPerPage=" + PAGE_SIZE
                            + "&pageID=" + page,
                    token,
                    null);
            JsonNode items = firstArray(
                    root,
                    "systems",
                    "appList",
                    "data");
            if (items == null || items.isEmpty()) break;
            for (JsonNode item : items) {
                String id = text(item, "id");
                if (id.isEmpty()) continue;
                JsonNode product = item.path("product");
                String itemProductId = product.isObject()
                        ? text(product, "id")
                        : firstText(item, "product", "productID");
                if (itemProductId.isEmpty()) itemProductId = productId;
                systems.add(new RemoteSystem(
                        id,
                        itemProductId,
                        firstText(item, "name", "title")));
            }
            if (items.size() < PAGE_SIZE) break;
        }
        return systems;
    }

    @Override
    public List<RemoteUser> listUsers(
            String baseUrl,
            String token) {
        List<RemoteUser> users = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            JsonNode root = request(
                    "GET",
                    apiBase(baseUrl) + "/users"
                            + "?recPerPage=" + PAGE_SIZE
                            + "&pageID=" + page,
                    token,
                    null);
            JsonNode items = firstArray(root, "users", "data");
            if (items == null || items.isEmpty()) break;
            for (JsonNode item : items) {
                if (booleanValue(item, "deleted")) continue;
                String account = firstText(
                        item,
                        "account",
                        "username",
                        "id");
                if (account.isEmpty()) continue;
                String name = firstText(
                        item,
                        "realname",
                        "realName",
                        "name");
                if (name.isEmpty()) name = account;
                users.add(new RemoteUser(account, name));
            }
            if (items.size() < PAGE_SIZE) break;
        }
        return users;
    }

    @Override
    public List<RemoteTestReport> listProductTestReports(
            String baseUrl,
            String token,
            String productId) {
        List<RemoteTestReport> reports = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            JsonNode root = request(
                    "GET",
                    apiBase(baseUrl) + "/products/" + productId
                            + "/testreports?recPerPage=" + PAGE_SIZE
                            + "&pageID=" + page,
                    token,
                    null);
            JsonNode items = firstArray(
                    root,
                    "testreports",
                    "reports",
                    "data");
            if (items == null || items.isEmpty()) break;
            for (JsonNode item : items) {
                String id = text(item, "id");
                if (id.isEmpty()) continue;
                reports.add(new RemoteTestReport(
                        id,
                        firstText(item, "title", "name")));
            }
            if (items.size() < PAGE_SIZE) break;
        }
        return reports;
    }

    @Override
    public CreatedBuild createBuild(
            String baseUrl,
            String token,
            CreateBuild createBuild) {
        ObjectNode body = objectMapper.createObjectNode();
        /*
         * ZenTao's form fields use execution while its v2 documentation
         * uses executionID. Send both so the same request works across
         * ZenTao minor versions.
         */
        putId(body, "project", createBuild.projectId());
        putId(body, "projectID", createBuild.projectId());
        putId(body, "execution", createBuild.executionId());
        putId(body, "executionID", createBuild.executionId());
        putId(body, "product", createBuild.productId());
        putId(body, "system", createBuild.systemId());
        body.put("name", createBuild.name());
        body.put("builder", createBuild.builder());
        body.put("date", createBuild.date());
        if (!createBuild.downloadUrl().isBlank()) {
            body.put("filePath", createBuild.downloadUrl());
        }
        JsonNode response = request(
                "POST",
                apiBase(baseUrl) + "/builds",
                token,
                body);
        String id = responseId(response);
        if (id.isEmpty()) {
            throw new DomainException(
                    "禅道已返回成功，但没有返回构建 ID");
        }
        String name = firstText(
                responseNode(response, "build"),
                "name",
                "title");
        if (name.isEmpty()) name = createBuild.name();
        return new CreatedBuild(id, name);
    }

    @Override
    public CreatedTestTask createTestTask(
            String baseUrl,
            String token,
            CreateTestTask createTestTask) {
        ObjectNode body = objectMapper.createObjectNode();
        putId(body, "project", createTestTask.projectId());
        putId(body, "projectID", createTestTask.projectId());
        putId(body, "execution", createTestTask.executionId());
        putId(body, "executionID", createTestTask.executionId());
        putId(body, "product", createTestTask.productId());
        putId(body, "productID", createTestTask.productId());
        putId(body, "build", createTestTask.buildId());
        body.put("name", createTestTask.name());
        body.put("begin", createTestTask.begin());
        body.put("end", createTestTask.end());
        if (!createTestTask.status().isBlank()) {
            body.put("status", createTestTask.status());
        }
        if (!createTestTask.owner().isBlank()) {
            body.put("owner", createTestTask.owner());
        }
        if (!createTestTask.members().isBlank()) {
            body.put("members", createTestTask.members());
        }
        if (!createTestTask.testReport().isBlank()) {
            putId(body, "testreport", createTestTask.testReport());
        }
        if (!createTestTask.types().isEmpty()) {
            var types = body.putArray("type");
            createTestTask.types().forEach(types::add);
        }
        JsonNode response = request(
                "POST",
                apiBase(baseUrl) + "/testtasks",
                token,
                body);
        String id = responseId(response);
        if (id.isEmpty()) {
            throw new DomainException(
                    "禅道已返回成功，但没有返回测试单 ID");
        }
        String name = firstText(
                responseNode(response, "testtask"),
                "name",
                "title");
        if (name.isEmpty()) name = createTestTask.name();
        return new CreatedTestTask(id, name);
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
    public void confirmBug(
            String baseUrl,
            String token,
            String remoteBugId,
            String assignedTo,
            String comment) {
        JsonNode current = request(
                "GET",
                apiBase(baseUrl) + "/bugs/" + remoteBugId,
                token,
                null);
        JsonNode currentBug = current.path("bug");
        ObjectNode body = objectMapper.createObjectNode();
        body.put("assignedTo", assignedTo);
        body.put("pri", currentBug.path("pri").asInt(3));
        body.put("type", text(currentBug, "type"));
        body.put("status", text(currentBug, "status"));
        body.put("deadline", text(currentBug, "deadline"));
        if (currentBug.has("mailto")) {
            body.set("mailto", currentBug.get("mailto"));
        }
        body.put("comment", comment);
        request(
                "PUT",
                apiBase(baseUrl) + "/bugs/" + remoteBugId + "/confirm",
                token,
                body);
        JsonNode refreshed = request(
                "GET",
                apiBase(baseUrl) + "/bugs/" + remoteBugId,
                token,
                null);
        if (!booleanValue(refreshed.path("bug"), "confirmed")) {
            throw new DomainException(
                    "禅道未确认本次操作，请检查 Bug 状态或操作权限");
        }
    }

    @Override
    public boolean commentBug(
            String baseUrl,
            String token,
            String remoteBugId,
            String comment,
            String marker) {
        String bugUrl = apiBase(baseUrl) + "/bugs/" + remoteBugId;
        JsonNode current = request(
                "GET",
                bugUrl,
                token,
                null);
        if (hasBugComment(current, marker)) return true;

        ObjectNode body = objectMapper.createObjectNode();
        body.put("objectType", "bug");
        body.put("objectID", remoteBugId);
        body.put("actioncomment", comment);
        request(
                "POST",
                apiBase(baseUrl) + "/actions/comment",
                token,
                body);
        JsonNode refreshed = request(
                "GET",
                bugUrl,
                token,
                null);
        if (!hasBugComment(refreshed, marker)) {
            throw new DomainException(
                    "禅道未写入测试备注，请检查 Bug 是否已关闭或当前账号是否有备注权限");
        }
        return false;
    }

    @Override
    public boolean hasBugComment(
            String baseUrl,
            String token,
            String remoteBugId,
            String marker) {
        JsonNode current = request(
                "GET",
                apiBase(baseUrl) + "/bugs/" + remoteBugId,
                token,
                null);
        return hasBugComment(current, marker);
    }

    @Override
    public List<UploadedBugFile> uploadBugFiles(
            String baseUrl,
            String token,
            String remoteBugId,
            List<UploadFile> files) {
        if (files == null || files.isEmpty()) return List.of();
        List<UploadedBugFile> uploaded = new ArrayList<>(files.size());
        for (UploadFile file : files) {
            JsonNode response = uploadBugFile(
                    baseUrl,
                    token,
                    remoteBugId,
                    file);
            String id = response.path("id").asText(
                    response.path("data").path("id").asText(""));
            String rawUrl = response.path("url").asText(
                    response.path("data").path("url").asText(""));
            if (id.isBlank() && rawUrl.isBlank()) {
                throw new DomainException("禅道上未返回测试截图地址");
            }
            uploaded.add(new UploadedBugFile(
                    id,
                    file.filename(),
                    absoluteUploadUrl(baseUrl, rawUrl)));
        }
        return List.copyOf(uploaded);
    }

    private JsonNode uploadBugFile(
            String baseUrl,
            String token,
            String remoteBugId,
            UploadFile file) {
        if (file.content() == null || file.content().length == 0) {
            throw new DomainException("测试截图内容为空");
        }
        if (file.content().length > MAX_MEDIA_BYTES) {
            throw new DomainException("单张测试截图不能超过 10MB");
        }
        String contentType = file.contentType() == null
                ? ""
                : file.contentType().trim().toLowerCase();
        if (!contentType.startsWith("image/")) {
            throw new DomainException("只能上传图片格式的测试截图");
        }

        String boundary = "----WorkCove" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeMultipartField(body, boundary, "objectType", "bug");
        writeMultipartField(body, boundary, "objectID", remoteBugId);
        writeMultipartFile(
                body,
                boundary,
                "file",
                safeFilename(file.filename()),
                contentType,
                file.content());
        writeUtf8(body, "--" + boundary + "--\r\n");

        HttpResponse<String> response = sendBytes(
                "POST",
                apiBase(baseUrl) + "/files",
                token,
                body.toByteArray(),
                "multipart/form-data; boundary=" + boundary);
        return parseResponse(response);
    }

    private static void writeMultipartField(
            ByteArrayOutputStream body,
            String boundary,
            String name,
            String value) {
        writeUtf8(body, "--" + boundary + "\r\n");
        writeUtf8(
                body,
                "Content-Disposition: form-data; name=\""
                        + name + "\"\r\n\r\n");
        writeUtf8(body, value + "\r\n");
    }

    private static void writeMultipartFile(
            ByteArrayOutputStream body,
            String boundary,
            String field,
            String filename,
            String contentType,
            byte[] content) {
        writeUtf8(body, "--" + boundary + "\r\n");
        writeUtf8(
                body,
                "Content-Disposition: form-data; name=\""
                        + field + "\"; filename=\""
                        + filename + "\"\r\n");
        writeUtf8(body, "Content-Type: " + contentType + "\r\n\r\n");
        body.writeBytes(content);
        writeUtf8(body, "\r\n");
    }

    private static void writeUtf8(
            ByteArrayOutputStream body,
            String value) {
        body.writeBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String safeFilename(String value) {
        String filename = value == null ? "screenshot.png" : value;
        return filename
                .replace("\\", "_")
                .replace("/", "_")
                .replace("\"", "_")
                .replace("\r", "")
                .replace("\n", "");
    }

    private static String absoluteUploadUrl(
            String baseUrl,
            String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) return "";
        try {
            URI base = URI.create(
                    baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
            return base.resolve(rawUrl).toString();
        } catch (IllegalArgumentException exception) {
            return rawUrl;
        }
    }

    private static boolean hasBugComment(
            JsonNode bugResponse,
            String marker) {
        for (JsonNode action : bugResponse.path("actions")) {
            if (action.path("comment").asText("").contains(marker)) {
                return true;
            }
        }
        return false;
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

    private HttpResponse<String> sendBytes(
            String method,
            String url,
            String token,
            byte[] body,
            String contentType) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "application/json")
                .header("Content-Type", contentType);
        if (token != null && !token.isBlank()) {
            builder.header("token", token);
            String cookie = cookieHeader(mediaCookies.get(token));
            if (!cookie.isBlank()) builder.header("Cookie", cookie);
        }
        builder.method(method, HttpRequest.BodyPublishers.ofByteArray(body));
        try {
            return httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString(
                            StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("禅道请求已中断", exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw new DomainException("无法上传测试截图到禅道");
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
        if (status.isEmpty()) {
            status = root.path("result").asText("");
        }
        if (response.statusCode() >= 400
                || (!status.isEmpty()
                && !"success".equalsIgnoreCase(status)
                && !"ok".equalsIgnoreCase(status))) {
            String message = errorText(root);
            throw new DomainException(message.isEmpty()
                    ? "禅道接口请求失败"
                    : message);
        }
        return root;
    }

    private static String errorText(JsonNode root) {
        for (String field : List.of("reason", "message", "error")) {
            JsonNode value = root.path(field);
            if (value.isValueNode()) {
                String text = value.asText("").trim();
                if (!text.isEmpty()) return text;
            }
            if (value.isObject() || value.isArray()) {
                String nested = firstTextValue(value);
                if (!nested.isEmpty()) return nested;
            }
        }
        return "";
    }

    private static String firstTextValue(JsonNode node) {
        if (node.isValueNode()) {
            return node.asText("").trim();
        }
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                String value = firstTextValue(fields.next().getValue());
                if (!value.isEmpty()) return value;
            }
        }
        if (node.isArray()) {
            for (JsonNode value : node) {
                String text = firstTextValue(value);
                if (!text.isEmpty()) return text;
            }
        }
        return "";
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

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field).trim();
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static JsonNode firstArray(JsonNode root, String... fields) {
        for (String field : fields) {
            JsonNode value = root.path(field);
            if (value.isArray()) return value;
        }
        return null;
    }

    private static JsonNode responseNode(JsonNode root, String field) {
        JsonNode value = root.path(field);
        return value.isObject() ? value : root.path("data");
    }

    private static String responseId(JsonNode root) {
        String id = firstText(root, "id", "ID");
        if (!id.isEmpty()) return id;
        for (String field : List.of("data", "build", "testtask")) {
            id = firstText(root.path(field), "id", "ID");
            if (!id.isEmpty()) return id;
        }
        return "";
    }

    private static void putId(
            ObjectNode body,
            String field,
            String value) {
        if (value != null && value.matches("\\d{1,18}")) {
            body.put(field, Long.parseLong(value));
        } else {
            body.put(field, value == null ? "" : value);
        }
    }

    private static boolean booleanValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return false;
        return value.asBoolean()
                || "1".equals(value.asText())
                || "true".equalsIgnoreCase(value.asText());
    }
}
