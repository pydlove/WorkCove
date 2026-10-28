package com.pydlove.workcove.development.infrastructure;

import com.pydlove.workcove.development.domain.port.IdeLauncher;
import com.pydlove.workcove.shared.domain.DomainException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 通过 macOS 本机安装的 IntelliJ IDEA 打开项目。 */
public final class MacIdeLauncher implements IdeLauncher {

    @Override
    public void openProject(String path) {
        requireMac();
        Path project = validateProject(path);
        Path executable = findIdeaExecutable(
                System.getenv("WORKCOVE_IDEA_PATH"),
                System.getProperty("user.home", ""));
        if (executable == null) {
            throw new DomainException("未找到 IntelliJ IDEA，请确认已安装");
        }
        try {
            new ProcessBuilder(
                    executable.toString(),
                    project.toString())
                    .redirectInput(ProcessBuilder.Redirect.DISCARD)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException exception) {
            throw new IllegalStateException("无法启动 IntelliJ IDEA", exception);
        }
    }

    static Path validateProject(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new DomainException("项目目录无效");
        }
        Path project = Path.of(rawPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(project)) {
            throw new DomainException("项目目录不存在或无法访问");
        }
        return project;
    }

    static List<Path> ideaCandidates(String configuredPath, String home) {
        List<String> candidates = new ArrayList<>();
        if (configuredPath != null && !configuredPath.isBlank()) {
            candidates.add(configuredPath);
        }
        candidates.add(
                "/Applications/IntelliJ IDEA.app/Contents/MacOS/idea");
        candidates.add(
                home + "/Applications/IntelliJ IDEA.app/Contents/MacOS/idea");
        candidates.add(
                "/Applications/IntelliJ IDEA CE.app/Contents/MacOS/idea");
        candidates.add(
                home
                        + "/Applications/JetBrains Toolbox/"
                        + "IntelliJ IDEA Ultimate.app/Contents/MacOS/idea");
        candidates.add(
                home
                        + "/Applications/JetBrains Toolbox/"
                        + "IntelliJ IDEA Community Edition.app/"
                        + "Contents/MacOS/idea");
        return candidates.stream()
                .map(Path::of)
                .toList();
    }

    private static Path findIdeaExecutable(
            String configuredPath,
            String home) {
        return ideaCandidates(configuredPath, home).stream()
                .filter(Files::isRegularFile)
                .findFirst()
                .orElse(null);
    }

    private static void requireMac() {
        if (!System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("mac")) {
            throw new DomainException(
                    "当前系统暂不支持使用 IntelliJ IDEA 打开项目");
        }
    }
}
