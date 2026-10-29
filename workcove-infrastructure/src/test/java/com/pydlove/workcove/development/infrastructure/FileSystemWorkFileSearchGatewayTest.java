package com.pydlove.workcove.development.infrastructure;

import com.pydlove.workcove.development.domain.model.WorkFileMatch;
import com.pydlove.workcove.shared.domain.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileSystemWorkFileSearchGatewayTest {

    @TempDir
    Path temporaryDirectory;

    private final FileSystemWorkFileSearchGateway gateway =
            new FileSystemWorkFileSearchGateway();

    @Test
    void findsNestedFilesIgnoringSpacesAndGeneratedDirectories()
            throws Exception {
        Path reportDirectory = temporaryDirectory.resolve("闪捷/10-述职/2026Q3");
        Files.createDirectories(reportDirectory);
        Path report = reportDirectory.resolve(
                "AI 安全产品线工作总结与工作规划V0.3.pptx");
        Files.writeString(report, "report");

        Path ignoredDirectory = temporaryDirectory.resolve(
                "node_modules/package");
        Files.createDirectories(ignoredDirectory);
        Files.writeString(
                ignoredDirectory.resolve("AI 安全产品线工作总结与工作规划.docx"),
                "ignored");

        List<WorkFileMatch> matches = gateway.search(
                List.of(temporaryDirectory.toString()),
                "AI安全产品线工作总结与工作规划");

        assertEquals(1, matches.size());
        assertEquals(report.getFileName().toString(), matches.get(0).name());
        assertEquals(report.toRealPath().toString(), matches.get(0).path());
    }

    @Test
    void onlyAcceptsFilesContainedInConfiguredRoots() throws Exception {
        Path inside = temporaryDirectory.resolve("inside.txt");
        Path outsideDirectory = Files.createTempDirectory("workcove-outside-");
        Path outside = outsideDirectory.resolve("outside.txt");
        try {
            Files.writeString(inside, "inside");
            Files.writeString(outside, "outside");

            assertEquals(
                    inside.toRealPath(),
                    FileSystemWorkFileSearchGateway.validateFile(
                            inside.toString(),
                            List.of(temporaryDirectory.toString())));
            assertThrows(
                    DomainException.class,
                    () -> FileSystemWorkFileSearchGateway.validateFile(
                            outside.toString(),
                            List.of(temporaryDirectory.toString())));
        } finally {
            Files.deleteIfExists(outside);
            Files.deleteIfExists(outsideDirectory);
        }
    }
}
