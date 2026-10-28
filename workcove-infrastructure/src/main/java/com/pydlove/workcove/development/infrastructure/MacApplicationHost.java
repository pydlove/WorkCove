package com.pydlove.workcove.development.infrastructure;

import com.pydlove.workcove.development.domain.port.ApplicationHost;
import com.pydlove.workcove.shared.domain.DomainException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** 通过 macOS 原生能力选择、启动和结束本地应用。 */
public final class MacApplicationHost implements ApplicationHost {

    private static final String CHOOSE_SCRIPT =
            "POSIX path of (choose file with prompt \"选择 8小时工作台应用\" "
                    + "of type {\"com.apple.application-bundle\"} "
                    + "default location (path to applications folder))";
    private static final String ICON_SCRIPT = """
            ObjC.import('AppKit')
            ObjC.import('Foundation')
            function run(argv) {
              const filePath = argv[0]
              const workspace = $.NSWorkspace.sharedWorkspace
              const image = workspace.iconForFile($(filePath))
              image.size = $.NSMakeSize(128, 128)
              const bitmap = $.NSBitmapImageRep.imageRepWithData(
                image.TIFFRepresentation
              )
              const png = bitmap.representationUsingTypeProperties(
                $.NSBitmapImageFileTypePNG,
                $()
              )
              return 'data:image/png;base64,' +
                png.base64EncodedStringWithOptions(0).js
            }
            """;

    @Override
    public SelectedApplication chooseApplication() {
        requireMac("当前系统暂不支持选择应用");
        Process process;
        try {
            process = new ProcessBuilder(
                    "/usr/bin/osascript",
                    "-e",
                    CHOOSE_SCRIPT)
                    .start();
        } catch (IOException exception) {
            throw new IllegalStateException("无法打开系统应用选择器", exception);
        }

        try {
            if (!process.waitFor(2, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                return null;
            }
            String output = new String(
                    process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8).trim();
            String errorOutput = new String(
                    process.getErrorStream().readAllBytes(),
                    StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0) {
                if (errorOutput.contains("User canceled")
                        || errorOutput.contains("(-128)")) {
                    return null;
                }
                throw new DomainException(errorOutput.isEmpty()
                        ? "系统应用选择器执行失败"
                        : errorOutput);
            }

            String selectedPath = output.lines()
                    .map(String::trim)
                    .filter(line -> line.startsWith("/"))
                    .reduce((first, second) -> second)
                    .orElse("");
            Path application = validateApplication(selectedPath);
            String fileName = application.getFileName().toString();
            String name = fileName.endsWith(".app")
                    ? fileName.substring(0, fileName.length() - 4)
                    : fileName;
            return new SelectedApplication(
                    application.toString(),
                    name,
                    readIcon(application));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IllegalStateException("应用选择已中断", exception);
        } catch (IOException exception) {
            process.destroyForcibly();
            throw new IllegalStateException("无法读取应用选择结果", exception);
        }
    }

    @Override
    public void openApplication(String path) {
        requireMac("当前系统暂不支持启动应用");
        Path application = validateApplication(path);
        try {
            Process process = new ProcessBuilder(
                    "/usr/bin/open",
                    application.toString())
                    .start();
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new DomainException("启动应用超时");
            }
            if (process.exitValue() != 0) {
                throw new DomainException("无法启动应用");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("启动应用已中断", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("无法启动应用", exception);
        }
    }

    @Override
    public Set<String> runningApplicationPaths(List<String> paths) {
        requireMac("当前系统暂不支持检测应用运行状态");
        if (paths == null || paths.isEmpty()) return Set.of();
        return matchingApplicationPaths(paths, readProcessList());
    }

    @Override
    public void forceQuitApplication(String path) {
        requireMac("当前系统暂不支持强制退出应用");
        Path application = validateApplication(path);
        String processList = readProcessList();
        List<Long> processIds = processIds(
                processList,
                application + "/Contents/MacOS/");

        boolean found = false;
        boolean failed = false;
        for (long processId : processIds) {
            found = true;
            try {
                ProcessHandle.of(processId).ifPresent(handle -> {
                    if (!handle.destroyForcibly()) {
                        throw new IllegalStateException("kill failed");
                    }
                });
            } catch (Exception exception) {
                failed = true;
            }
        }

        if (!found) throw new DomainException("应用未在运行");
        if (failed) throw new DomainException("部分应用进程未能退出");
    }

    static Set<String> matchingApplicationPaths(
            List<String> paths,
            String processList) {
        Map<String, String> applications = new LinkedHashMap<>();
        for (String path : paths) {
            if (path == null || path.isBlank()) continue;
            Path application = Path.of(path).toAbsolutePath().normalize();
            if (!application.toString().endsWith(".app")) continue;
            applications.put(
                    application + "/Contents/MacOS/",
                    path);
        }

        Set<String> running = new LinkedHashSet<>();
        for (String line : processList.split("\\R")) {
            for (Map.Entry<String, String> application : applications.entrySet()) {
                if (line.contains(application.getKey())) {
                    running.add(application.getValue());
                }
            }
        }
        return running;
    }

    private String readProcessList() {
        try {
            Process process = new ProcessBuilder(
                    "/bin/ps",
                    "-axo",
                    "pid=,command=")
                    .start();
            String processList = new String(
                    process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            process.getErrorStream().readAllBytes();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new DomainException("读取应用进程超时");
            }
            if (process.exitValue() != 0) {
                throw new DomainException("无法读取应用进程");
            }
            return processList;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("读取应用进程已中断", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取应用进程", exception);
        }
    }

    private static List<Long> processIds(
            String processList,
            String executablePrefix) {
        List<Long> processIds = new ArrayList<>();
        for (String line : processList.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.contains(executablePrefix)) continue;
            String pidText = trimmed.split("\\s+", 2)[0];
            try {
                processIds.add(Long.parseLong(pidText));
            } catch (NumberFormatException ignored) {
                // Ignore malformed ps output and continue checking other rows.
            }
        }
        return processIds;
    }

    private Path validateApplication(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new DomainException("应用路径无效");
        }
        Path application = Path.of(rawPath).toAbsolutePath().normalize();
        if (!application.toString().endsWith(".app")
                || !Files.isDirectory(application)) {
            throw new DomainException("应用不存在或不是有效的 macOS 应用");
        }
        return application;
    }

    private String readIcon(Path application) {
        try {
            Process process = new ProcessBuilder(
                    "/usr/bin/osascript",
                    "-l",
                    "JavaScript",
                    "-e",
                    ICON_SCRIPT,
                    application.toString())
                    .start();
            byte[] output = process.getInputStream().readAllBytes();
            process.getErrorStream().readAllBytes();
            if (!process.waitFor(30, TimeUnit.SECONDS)
                    || process.exitValue() != 0) {
                process.destroyForcibly();
                return null;
            }
            String icon = new String(output, StandardCharsets.UTF_8).trim();
            return icon.startsWith("data:image/") ? icon : null;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        } catch (IOException exception) {
            return null;
        }
    }

    private void requireMac(String message) {
        if (!System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("mac")) {
            throw new DomainException(message);
        }
    }
}
