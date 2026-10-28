package com.pydlove.workcove.development.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pydlove.workcove.development.domain.port.SogouInputStatsGateway;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class MacSogouInputStatsGateway
        implements SogouInputStatsGateway {

    private static final String HELPER_VERSION = "v1";
    private static final String SOURCE_RESOURCE =
            "/native/sogou-stat-query.m";
    private static final List<Path> APP_PATHS = List.of(
            Path.of("/Library/Input Methods/SogouInput.app"),
            Path.of(System.getProperty("user.home", ""),
                    "Library/Input Methods/SogouInput.app"));

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public MacSogouInputStatsGateway(
            ObjectMapper objectMapper,
            Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public Stats read() {
        Instant checkedAt = clock.instant();
        if (!System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("mac")) {
            return unavailable(
                    false,
                    null,
                    "搜狗输入法统计仅支持 macOS",
                    checkedAt);
        }
        Path appPath = APP_PATHS.stream()
                .filter(Files::isDirectory)
                .findFirst()
                .orElse(null);
        if (appPath == null) {
            return unavailable(
                    false,
                    null,
                    "未安装搜狗输入法",
                    checkedAt);
        }

        try {
            Path helper = ensureHelper();
            Process process = new ProcessBuilder(helper.toString())
                    .redirectErrorStream(false)
                    .start();
            boolean finished = process.waitFor(15, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return unavailable(
                        true,
                        appPath.toString(),
                        "读取搜狗统计超时",
                        checkedAt);
            }
            String output = new String(
                    process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8).trim();
            String error = new String(
                    process.getErrorStream().readAllBytes(),
                    StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0 || output.isEmpty()) {
                return unavailable(
                        true,
                        appPath.toString(),
                        error.isEmpty() ? "无法读取搜狗统计" : error,
                        checkedAt);
            }

            JsonNode root = objectMapper.readTree(output);
            boolean available = root.path("serviceAvailable").asBoolean(false);
            List<DailyCount> days = new ArrayList<>();
            JsonNode dayNodes = root.path("days");
            if (available && dayNodes.isArray()) {
                for (JsonNode day : dayNodes) {
                    String date = day.path("date").asText("").trim();
                    if (!date.matches("\\d{4}-\\d{2}-\\d{2}")) continue;
                    days.add(new DailyCount(
                            date,
                            Math.max(0, day.path("count").asLong(0))));
                }
            }
            long historySpeedAt = root.path("historySpeedAt").asLong(0);
            return new Stats(
                    true,
                    available,
                    appPath.toString(),
                    Math.max(0, root.path("totalInput").asLong(0)),
                    Math.max(0, root.path("currentSpeed").asLong(0)),
                    Math.max(0, root.path("historySpeed").asLong(0)),
                    historySpeedAt > 0 ? historySpeedAt : null,
                    available ? days : List.of(),
                    available
                            ? ""
                            : root.path("message").asText(
                                    "搜狗输入法统计服务不可用"),
                    checkedAt);
        } catch (Exception exception) {
            return unavailable(
                    true,
                    appPath.toString(),
                    "无法读取搜狗输入法统计："
                            + messageOf(exception),
                    checkedAt);
        }
    }

    private synchronized Path ensureHelper() throws IOException {
        Path directory = Path.of(
                System.getProperty("user.home", ""),
                ".workcove",
                "tools");
        Files.createDirectories(directory);
        Path source = directory.resolve(
                "sogou-stat-query-" + HELPER_VERSION + ".m");
        Path helper = directory.resolve(
                "sogou-stat-query-" + HELPER_VERSION);
        if (Files.isExecutable(helper)) return helper;

        try (InputStream resource = MacSogouInputStatsGateway.class
                .getResourceAsStream(SOURCE_RESOURCE)) {
            if (resource == null) {
                throw new IOException("缺少搜狗统计辅助程序源码");
            }
            Files.copy(
                    resource,
                    source,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        Path compiler = Path.of("/usr/bin/clang");
        if (!Files.isExecutable(compiler)) {
            throw new IOException("未找到系统 C 编译器 clang");
        }
        Process compile = new ProcessBuilder(
                compiler.toString(),
                "-fobjc-arc",
                "-framework",
                "Foundation",
                "-x",
                "objective-c",
                source.toString(),
                "-o",
                helper.toString())
                .redirectErrorStream(true)
                .start();
        String compileOutput;
        try {
            if (!compile.waitFor(30, TimeUnit.SECONDS)) {
                compile.destroyForcibly();
                throw new IOException("编译搜狗统计辅助程序超时");
            }
            compileOutput = new String(
                    compile.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8).trim();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            compile.destroyForcibly();
            throw new IOException("编译搜狗统计辅助程序被中断", exception);
        }
        if (compile.exitValue() != 0 || !Files.isExecutable(helper)) {
            throw new IOException(compileOutput.isEmpty()
                    ? "编译搜狗统计辅助程序失败"
                    : compileOutput);
        }
        return helper;
    }

    private static Stats unavailable(
            boolean installed,
            String appPath,
            String message,
            Instant checkedAt) {
        return new Stats(
                installed,
                false,
                appPath,
                0,
                0,
                0,
                null,
                List.of(),
                message,
                checkedAt);
    }

    private static String messageOf(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
