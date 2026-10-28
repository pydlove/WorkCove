package com.pydlove.workcove.development.infrastructure;

import com.pydlove.workcove.shared.domain.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileZentaoTestEvidenceStoreTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void preparesListsAndReadsEvidenceInsideTheBugRunDirectory()
            throws Exception {
        FileZentaoTestEvidenceStore store =
                new FileZentaoTestEvidenceStore(
                        temporaryDirectory.resolve("evidence"));
        var prepared = store.prepare(
                7,
                "workspace-1",
                42,
                "run-1");
        Path screenshot = Path.of(prepared.directory())
                .resolve("bug-fixed.png");
        byte[] content = new byte[] {
                (byte) 0x89,
                0x50,
                0x4e,
                0x47
        };
        Files.write(screenshot, content);

        var evidence = store.list(
                7,
                "workspace-1",
                42,
                "run-1");
        var file = store.find(
                7,
                "workspace-1",
                42,
                "run-1",
                "bug-fixed.png");

        assertEquals(1, evidence.size());
        assertEquals("bug-fixed.png", evidence.get(0).filename());
        assertEquals("image/png", evidence.get(0).contentType());
        assertTrue(file.isPresent());
        assertArrayEquals(content, file.orElseThrow().content());
    }

    @Test
    void rejectsUnsafeIdentifiersAndFilenames() {
        FileZentaoTestEvidenceStore store =
                new FileZentaoTestEvidenceStore(
                        temporaryDirectory.resolve("evidence"));

        assertThrows(
                DomainException.class,
                () -> store.prepare(7, "../escape", 42, "run-1"));
        assertThrows(
                DomainException.class,
                () -> store.find(
                        7,
                        "workspace-1",
                        42,
                        "run-1",
                        "../secret.png"));
    }
}
