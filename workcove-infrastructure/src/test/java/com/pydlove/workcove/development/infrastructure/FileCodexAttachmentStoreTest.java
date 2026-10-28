package com.pydlove.workcove.development.infrastructure;

import com.pydlove.workcove.development.domain.model.CodexAttachment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileCodexAttachmentStoreTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void storesFilesByOwnerAndRejectsCrossUserLookup() throws Exception {
        FileCodexAttachmentStore store =
                new FileCodexAttachmentStore(temporaryDirectory);

        CodexAttachment attachment = store.store(
                11L,
                "notes.txt",
                "text/plain",
                3,
                new ByteArrayInputStream("abc".getBytes()));

        assertEquals(3, Files.size(attachment.path()));
        assertTrue(store.find(11L, attachment.id()).isPresent());
        assertFalse(store.find(12L, attachment.id()).isPresent());
        assertTrue(attachment.path().startsWith(
                temporaryDirectory.resolve("11")));
    }
}
