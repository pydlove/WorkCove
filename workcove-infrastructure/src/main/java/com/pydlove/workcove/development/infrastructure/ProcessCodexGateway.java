package com.pydlove.workcove.development.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pydlove.workcove.development.domain.model.CodexAttachment;
import com.pydlove.workcove.development.domain.port.CodexGateway;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** 通过 JSONL 协议运行本机 codex app-server。 */
public final class ProcessCodexGateway implements CodexGateway {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(90);
    private static final Duration INITIALIZE_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration INTERRUPT_TIMEOUT = Duration.ofSeconds(10);

    private final ObjectMapper objectMapper;
    private final AtomicLong requestIds = new AtomicLong();
    private final Map<Long, CompletableFuture<JsonNode>> pending =
            new ConcurrentHashMap<>();
    private final Set<Consumer<Map<String, Object>>> listeners =
            new CopyOnWriteArraySet<>();

    private Process process;
    private BufferedWriter input;
    private Thread outputThread;
    private Thread errorThread;
    private StringBuilder stderr = new StringBuilder();
    private String executable = findExecutable();

    public ProcessCodexGateway(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isAvailable() {
        if (executable == null) {
            executable = findExecutable();
        }
        return executable != null;
    }

    @Override
    public boolean isConnected() {
        return process != null && process.isAlive();
    }

    @Override
    public String executable() {
        if (!isAvailable()) {
            return "";
        }
        return executable;
    }

    @Override
    public Runnable subscribe(Consumer<Map<String, Object>> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    @Override
    public Map<String, Object> startThread(String cwd, String sandbox) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("cwd", directoryOrHome(cwd));
        params.put("approvalPolicy", "never");
        params.put("sandbox", sandboxMode(sandbox));
        return request("thread/start", params);
    }

    @Override
    public Map<String, Object> resumeThread(
            String threadId,
            String cwd,
            String sandbox) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("threadId", requireText(threadId, "会话 ID 无效"));
        if (cwd != null && !cwd.isBlank()) {
            params.put("cwd", cwd);
        }
        params.put("approvalPolicy", "never");
        params.put("sandbox", sandboxMode(sandbox));
        return request("thread/resume", params);
    }

    @Override
    public Map<String, Object> startTurn(
            String threadId,
            String text,
            String cwd,
            List<CodexAttachment> attachments,
            String model,
            String reasoningEffort,
            List<String> writableRoots) {
        return request(
                "turn/start",
                turnParams(
                        threadId,
                        text,
                        cwd,
                        attachments,
                        model,
                        reasoningEffort,
                        writableRoots));
    }

    Map<String, Object> turnParams(
            String threadId,
            String text,
            String cwd,
            List<CodexAttachment> attachments,
            String model,
            String reasoningEffort,
            List<String> writableRoots) {
        List<Map<String, Object>> input = new ArrayList<>();
        if (text != null && !text.isBlank()) {
            Map<String, Object> textItem = new LinkedHashMap<>();
            textItem.put("type", "text");
            textItem.put("text", text.trim());
            textItem.put("text_elements", List.of());
            input.add(textItem);
        }
        if (attachments != null) {
            for (CodexAttachment attachment : attachments) {
                Map<String, Object> item = new LinkedHashMap<>();
                if (attachment.image()) {
                    item.put("type", "localImage");
                } else {
                    item.put("type", "mention");
                    item.put("name", attachment.name());
                }
                item.put("path", attachment.path().toString());
                input.add(item);
            }
        }
        if (input.isEmpty()) {
            throw new IllegalArgumentException("消息或附件不能为空");
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("threadId", requireText(threadId, "会话 ID 无效"));
        params.put("input", input);
        if (cwd != null && !cwd.isBlank()) {
            params.put("cwd", cwd);
        }
        if (model != null && !model.isBlank()) {
            params.put("model", model.trim());
        }
        if (reasoningEffort != null && !reasoningEffort.isBlank()) {
            params.put("effort", reasoningEffort.trim());
        }
        if (writableRoots != null && !writableRoots.isEmpty()) {
            List<String> rootValues = new ArrayList<>();
            if (cwd != null && !cwd.isBlank()) {
                rootValues.add(cwd);
            }
            rootValues.addAll(writableRoots);
            List<String> roots = rootValues.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .map(Path::of)
                    .map(path -> path.toAbsolutePath().normalize())
                    .map(Path::toString)
                    .distinct()
                    .toList();
            if (!roots.isEmpty()) {
                params.put("sandboxPolicy", Map.of(
                        "type", "workspaceWrite",
                        "writableRoots", roots,
                        "networkAccess", true,
                        "excludeTmpdirEnvVar", false,
                        "excludeSlashTmp", false));
            }
        }
        return params;
    }

    @Override
    public Map<String, Object> listModels() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("limit", 50);
        params.put("includeHidden", false);
        return request("model/list", params);
    }

    @Override
    public Map<String, Object> interruptTurn(String threadId, String turnId) {
        String validThreadId = requireText(threadId, "会话 ID 无效");
        String validTurnId = requireText(turnId, "任务 ID 无效");
        if (!isConnected()) {
            return interruptedResult(false);
        }
        try {
            return requestOnRunningServer(
                    "turn/interrupt",
                    Map.of(
                            "threadId",
                            validThreadId,
                            "turnId",
                            validTurnId),
                    INTERRUPT_TIMEOUT);
        } catch (RuntimeException exception) {
            close();
            emitBridgeExit(
                    "Codex app-server 未能响应停止请求，已重启服务并结束当前任务");
            return interruptedResult(true);
        }
    }

    @Override
    public Map<String, Object> setThreadName(String threadId, String name) {
        return request(
                "thread/name/set",
                Map.of(
                        "threadId",
                        requireText(threadId, "会话 ID 无效"),
                        "name",
                        requireText(name, "会话名称不能为空")));
    }

    private synchronized void ensureStarted() {
        if (process != null && process.isAlive()) {
            return;
        }
        if (!isAvailable()) {
            throw new IllegalStateException(
                    "未找到 Codex CLI，请先安装并登录 Codex");
        }

        try {
            ProcessBuilder builder = new ProcessBuilder(executable, "app-server");
            builder.directory(Path.of(System.getProperty("user.home")).toFile());
            Path executableDirectory = Path.of(executable)
                    .toAbsolutePath()
                    .getParent();
            if (executableDirectory != null) {
                String currentPath = builder.environment()
                        .getOrDefault("PATH", "");
                builder.environment().put(
                        "PATH",
                        currentPath.isBlank()
                                ? executableDirectory.toString()
                                : executableDirectory
                                        + File.pathSeparator
                                        + currentPath);
            }
            process = builder.start();
            input = new BufferedWriter(
                    new OutputStreamWriter(
                            process.getOutputStream(),
                            StandardCharsets.UTF_8));
            stderr = new StringBuilder();
            startReaderThreads();

            Map<String, Object> clientInfo = new LinkedHashMap<>();
            clientInfo.put("name", "workcove_web");
            clientInfo.put("title", "8小时工作台 Codex");
            clientInfo.put("version", "0.1.0");

            Map<String, Object> initializeParams = new LinkedHashMap<>();
            initializeParams.put("clientInfo", clientInfo);
            initializeParams.put("capabilities", null);
            requestOnRunningServer(
                    "initialize",
                    initializeParams,
                    INITIALIZE_TIMEOUT);
            notifyServer("initialized", Map.of());
        } catch (IOException exception) {
            close();
            throw new IllegalStateException(
                    "无法启动 Codex app-server",
                    exception);
        } catch (RuntimeException exception) {
            close();
            throw exception;
        }
    }

    private void startReaderThreads() {
        outputThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            process.getInputStream(),
                            StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isBlank()) {
                        handleLine(line);
                    }
                }
            } catch (IOException exception) {
                emitBridgeExit("读取 Codex 输出失败：" + exception.getMessage());
            } finally {
                if (process != null && !process.isAlive()) {
                    IllegalStateException error =
                            new IllegalStateException(
                                    "Codex app-server 进程已退出");
                    pending.values().forEach(future ->
                            future.completeExceptionally(error));
                    pending.clear();
                    emitBridgeExit(error.getMessage());
                }
            }
        }, "workcove-codex-output");
        outputThread.setDaemon(true);
        outputThread.start();

        errorThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            process.getErrorStream(),
                            StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (stderr.length() > 0) {
                        stderr.append('\n');
                    }
                    stderr.append(line);
                    if (stderr.length() > 12_000) {
                        stderr.delete(0, stderr.length() - 12_000);
                    }
                }
            } catch (IOException ignored) {
                // The output thread owns the process exit event.
            }
        }, "workcove-codex-error");
        errorThread.setDaemon(true);
        errorThread.start();
    }

    private void handleLine(String line) {
        try {
            JsonNode message = objectMapper.readTree(line);
            JsonNode methodNode = message.get("method");
            JsonNode idNode = message.get("id");

            if (methodNode != null && idNode != null) {
                Map<String, Object> event = eventMap(
                        "serverRequest",
                        methodNode.asText(),
                        message.get("params"));
                event.put("requestId", idNode.asLong());
                emit(event);
                return;
            }

            if (idNode != null) {
                CompletableFuture<JsonNode> future =
                        pending.remove(idNode.asLong());
                if (future == null) {
                    return;
                }
                JsonNode errorNode = message.get("error");
                if (errorNode != null && !errorNode.isNull()) {
                    String detail = errorNode.path("message").asText(
                            "Codex 请求失败");
                    future.completeExceptionally(
                            new IllegalStateException(detail));
                } else {
                    future.complete(message.path("result"));
                }
                return;
            }

            if (methodNode != null) {
                emit(eventMap(
                        "notification",
                        methodNode.asText(),
                        message.get("params")));
            }
        } catch (IOException exception) {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", "bridge");
            event.put("method", "appServer/invalidMessage");
            event.put("params", Map.of(
                    "message",
                    "Codex 协议消息解析失败"));
            emit(event);
        }
    }

    private Map<String, Object> eventMap(
            String type,
            String method,
            JsonNode params) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        event.put("method", method);
        event.put(
                "params",
                params == null
                        ? Map.of()
                        : objectMapper.convertValue(
                                params,
                                new TypeReference<Map<String, Object>>() {
                                }));
        event.put("emittedAtMs", System.currentTimeMillis());
        return event;
    }

    private Map<String, Object> request(
            String method,
            Map<String, Object> params) {
        ensureStarted();
        return requestOnRunningServer(method, params);
    }

    private Map<String, Object> requestOnRunningServer(
            String method,
            Map<String, Object> params) {
        return requestOnRunningServer(method, params, REQUEST_TIMEOUT);
    }

    private Map<String, Object> requestOnRunningServer(
            String method,
            Map<String, Object> params,
            Duration timeout) {
        long id = requestIds.incrementAndGet();
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pending.put(id, future);

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("method", method);
        message.put("id", id);
        message.put("params", params);
        send(message);

        try {
            JsonNode result = future.get(
                    timeout.toMillis(),
                    TimeUnit.MILLISECONDS);
            return objectMapper.convertValue(
                    result,
                    new TypeReference<Map<String, Object>>() {
                    });
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            pending.remove(id);
            throw new IllegalStateException("Codex 请求已中断", exception);
        } catch (TimeoutException exception) {
            pending.remove(id);
            throw new IllegalStateException("Codex 请求超时：" + method);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            throw new IllegalStateException(
                    cause == null ? "Codex 请求失败" : cause.getMessage(),
                    cause);
        }
    }

    private void notifyServer(String method, Map<String, Object> params) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("method", method);
        message.put("params", params);
        send(message);
    }

    private synchronized void send(Map<String, Object> message) {
        if (input == null || process == null || !process.isAlive()) {
            throw new IllegalStateException("Codex app-server 未连接");
        }
        try {
            input.write(objectMapper.writeValueAsString(message));
            input.newLine();
            input.flush();
        } catch (IOException exception) {
            throw new IllegalStateException("无法向 Codex 发送请求", exception);
        }
    }

    private void emitBridgeExit(String message) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "bridge");
        event.put("method", "appServer/exit");
        event.put("params", Map.of("message", message));
        emit(event);
    }

    private static Map<String, Object> interruptedResult(boolean forced) {
        return Map.of(
                "alreadyStopped",
                true,
                "forced",
                forced);
    }

    private void emit(Map<String, Object> event) {
        for (Consumer<Map<String, Object>> listener : listeners) {
            listener.accept(event);
        }
    }

    private String directoryOrHome(String cwd) {
        if (cwd == null || cwd.isBlank()) {
            return System.getProperty("user.home");
        }
        Path path = Path.of(cwd);
        if (!path.isAbsolute() || !Files.isDirectory(path)) {
            throw new IllegalArgumentException("Codex 项目目录无效");
        }
        return path.normalize().toString();
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String sandboxMode(String value) {
        if (value == null || value.isBlank()) {
            return "workspace-write";
        }
        String sandbox = value.trim().toLowerCase(Locale.ROOT);
        if (!Set.of(
                "workspace-write",
                "danger-full-access",
                "read-only").contains(sandbox)) {
            throw new IllegalArgumentException("不支持的 Codex 沙箱模式");
        }
        return sandbox;
    }

    private String findExecutable() {
        String explicit = System.getenv("WORKCOVE_CODEX_PATH");
        if (explicit != null && !explicit.isBlank()
                && Files.isExecutable(Path.of(explicit))) {
            return explicit;
        }

        String home = System.getProperty("user.home");
        List<String> candidates = new ArrayList<>(List.of(
                "/opt/homebrew/bin/codex",
                "/usr/local/bin/codex",
                home + "/.local/bin/codex",
                home + "/.npm-global/bin/codex"));

        Path nvmRoot = Path.of(home, ".nvm", "versions", "node");
        if (Files.isDirectory(nvmRoot)) {
            try (Stream<Path> versions = Files.list(nvmRoot)) {
                versions.filter(Files::isDirectory)
                        .sorted(Comparator.comparing(Path::getFileName)
                                .reversed())
                        .map(path -> path.resolve("bin/codex"))
                        .map(Path::toString)
                        .forEach(candidates::add);
            } catch (IOException ignored) {
                // Fall through to PATH lookup.
            }
        }

        for (String candidate : candidates) {
            Path path = Path.of(candidate);
            if (Files.isExecutable(path)) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    public void close() {
        if (process != null) {
            process.destroy();
            try {
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
        process = null;
        input = null;
        pending.values().forEach(future ->
                future.completeExceptionally(
                        new IllegalStateException("Codex app-server 已关闭")));
        pending.clear();
    }
}
