package com.pydlove.workcove.development.infrastructure;

import com.pydlove.workcove.development.domain.port.DirectoryPicker;
import com.pydlove.workcove.shared.domain.DomainException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** 通过 macOS Finder 选择并打开本地目录。 */
public final class MacDirectoryPicker implements DirectoryPicker {

    private static final String SCRIPT =
            "POSIX path of (choose folder with prompt \"选择 8小时工作台项目目录\")";

    @Override
    public String chooseDirectory() {
        if (!System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("mac")) {
            return null;
        }

        Process process;
        try {
            process = new ProcessBuilder(
                    "/usr/bin/osascript",
                    "-e",
                    SCRIPT)
                    .start();
        } catch (IOException exception) {
            throw new IllegalStateException("无法打开系统目录选择器", exception);
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
                        ? "系统目录选择器执行失败"
                        : errorOutput);
            }

            String selectedPath = output.lines()
                    .map(String::trim)
                    .filter(line -> line.startsWith("/"))
                    .reduce((first, second) -> second)
                    .orElse("");
            if (selectedPath.isEmpty()) {
                throw new DomainException("系统目录选择器未返回有效路径");
            }

            Path directory = validateDirectory(selectedPath);
            return directory.normalize().toString();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IllegalStateException("目录选择已中断", exception);
        } catch (IOException exception) {
            process.destroyForcibly();
            throw new IllegalStateException("无法读取目录选择结果", exception);
        }
    }

    @Override
    public void openDirectory(String path) {
        requireMac();
        Path directory = validateDirectory(path);
        try {
            Process process = new ProcessBuilder(
                    "/usr/bin/open",
                    directory.toString())
                    .start();
            process.getErrorStream().readAllBytes();
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new DomainException("打开访达超时");
            }
            if (process.exitValue() != 0) {
                throw new DomainException("无法在访达中打开目录");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("打开访达已中断", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("无法打开访达", exception);
        }
    }

    static Path validateDirectory(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new DomainException("目录路径无效");
        }
        Path directory = Path.of(rawPath).toAbsolutePath().normalize();
        if (!directory.isAbsolute() || !Files.isDirectory(directory)) {
            throw new DomainException("目录不存在或无法访问");
        }
        return directory;
    }

    private static void requireMac() {
        if (!System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("mac")) {
            throw new DomainException("当前系统暂不支持通过访达打开目录");
        }
    }
}
