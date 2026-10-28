package com.pydlove.workcove.development.infrastructure;

import com.pydlove.workcove.shared.domain.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MacIdeLauncherTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void validatesAnExistingProjectDirectory() {
        assertEquals(
                temporaryDirectory.toAbsolutePath().normalize(),
                MacIdeLauncher.validateProject(
                        temporaryDirectory.toString()));
    }

    @Test
    void rejectsMissingPathsAndFiles() throws Exception {
        Path file = temporaryDirectory.resolve("file.txt");
        Files.writeString(file, "test");

        assertThrows(
                DomainException.class,
                () -> MacIdeLauncher.validateProject(" "));
        assertThrows(
                DomainException.class,
                () -> MacIdeLauncher.validateProject(
                        temporaryDirectory.resolve("missing").toString()));
        assertThrows(
                DomainException.class,
                () -> MacIdeLauncher.validateProject(file.toString()));
    }

    @Test
    void includesConfiguredAndStandardInstallLocations() {
        List<Path> candidates = MacIdeLauncher.ideaCandidates(
                "/custom/idea",
                "/Users/tester");

        assertEquals(Path.of("/custom/idea"), candidates.get(0));
        assertTrue(candidates.contains(
                Path.of("/Applications/IntelliJ IDEA.app/Contents/MacOS/idea")));
        assertTrue(candidates.contains(Path.of(
                "/Users/tester/Applications/JetBrains Toolbox/"
                        + "IntelliJ IDEA Ultimate.app/Contents/MacOS/idea")));
    }
}
