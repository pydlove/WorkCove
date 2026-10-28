package com.pydlove.workcove.development.infrastructure;

import com.pydlove.workcove.development.domain.model.ServerTool;
import com.pydlove.workcove.development.domain.port.ServerLauncher;
import com.pydlove.workcove.shared.domain.DomainException;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class MacServerLauncher implements ServerLauncher {

    @Override
    public LaunchResult open(ServerConnection connection, ServerTool tool) {
        if (!isMac()) {
            throw new DomainException("当前系统不支持快捷打开服务器客户端");
        }
        if (tool == ServerTool.FILEZILLA) {
            openFileZilla(connection);
        } else {
            openTermius(connection);
        }
        return new LaunchResult(copyPassword(connection.password()));
    }

    private void openTermius(ServerConnection connection) {
        Path app = firstExisting(
                System.getenv("WORKCOVE_TERMIUS_APP"),
                "/Applications/Termius.app",
                Path.of(System.getProperty("user.home"), "Applications/Termius.app")
                        .toString());
        if (app == null) {
            throw new DomainException("未找到 Termius");
        }
        String url = sshUri(connection, "ssh").toString();
        runAndWait(
                List.of("/usr/bin/open", "-a", app.toString(), url),
                "无法启动 Termius");
    }

    private void openFileZilla(ServerConnection connection) {
        Path executable = firstExisting(
                System.getenv("WORKCOVE_FILEZILLA_EXECUTABLE"),
                System.getenv("WORKCOVE_FILEZILLA_APP"),
                "/Applications/FileZilla 2.app/Contents/MacOS/filezilla",
                "/Applications/FileZilla.app/Contents/MacOS/filezilla",
                Path.of(
                        System.getProperty("user.home"),
                        "Applications/FileZilla 2.app/Contents/MacOS/filezilla")
                        .toString());
        if (executable == null) {
            throw new DomainException("未找到 FileZilla");
        }
        String url = sshUri(connection, "sftp").toString();
        try {
            new ProcessBuilder(executable.toString(), url)
                    .redirectInput(ProcessBuilder.Redirect.DISCARD)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException exception) {
            throw new DomainException("无法启动 FileZilla");
        }
    }

    private boolean copyPassword(String password) {
        if (password == null || password.isEmpty()) return false;
        try {
            Process process = new ProcessBuilder("/usr/bin/pbcopy")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            process.getOutputStream().write(password.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().close();
            return process.waitFor(3, TimeUnit.SECONDS)
                    && process.exitValue() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void runAndWait(List<String> command, String message) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(5, TimeUnit.SECONDS)
                    || process.exitValue() != 0) {
                throw new DomainException(message);
            }
        } catch (IOException exception) {
            throw new DomainException(message);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DomainException(message);
        }
    }

    private static URI sshUri(ServerConnection connection, String scheme) {
        try {
            return new URI(
                    scheme,
                    connection.username(),
                    connection.host(),
                    connection.port(),
                    "/",
                    null,
                    null);
        } catch (Exception exception) {
            throw new DomainException("服务器连接地址无效");
        }
    }

    private static Path firstExisting(String... candidates) {
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) continue;
            Path path = Path.of(candidate);
            if (Files.exists(path)) return path;
        }
        return null;
    }

    private static boolean isMac() {
        return System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("mac");
    }
}
