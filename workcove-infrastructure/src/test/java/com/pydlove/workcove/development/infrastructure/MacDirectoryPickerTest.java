package com.pydlove.workcove.development.infrastructure;

import com.pydlove.workcove.shared.domain.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MacDirectoryPickerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void acceptsAnExistingAbsoluteDirectory() {
        assertEquals(
                temporaryDirectory.toAbsolutePath().normalize(),
                MacDirectoryPicker.validateDirectory(
                        temporaryDirectory.toString()));
    }

    @Test
    void rejectsBlankMissingAndFilePaths() throws Exception {
        Path file = temporaryDirectory.resolve("file.txt");
        java.nio.file.Files.writeString(file, "test");

        assertThrows(
                DomainException.class,
                () -> MacDirectoryPicker.validateDirectory(" "));
        assertThrows(
                DomainException.class,
                () -> MacDirectoryPicker.validateDirectory(
                        temporaryDirectory.resolve("missing").toString()));
        assertThrows(
                DomainException.class,
                () -> MacDirectoryPicker.validateDirectory(file.toString()));
    }
}
