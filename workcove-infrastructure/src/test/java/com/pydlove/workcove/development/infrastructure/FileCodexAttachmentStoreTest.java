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
    void storesFilesByOwnerAndWorkspace() throws Exception {
        FileCodexAttachmentStore store =
                new FileCodexAttachmentStore(temporaryDirectory);

        CodexAttachment attachment = store.store(
                11L,
                "workspace-a",
                "notes.txt",
                "text/plain",
                3,
                new ByteArrayInputStream("abc".getBytes()));

        assertEquals(3, Files.size(attachment.path()));
        assertTrue(store.find(
                11L,
                "workspace-a",
                attachment.id()).isPresent());
        assertFalse(store.find(
                12L,
                "workspace-a",
                attachment.id()).isPresent());
        assertFalse(store.find(
                11L,
                "workspace-b",
                attachment.id()).isPresent());
        assertTrue(attachment.path().startsWith(
                temporaryDirectory.resolve("11").resolve("workspace-a")));

        FileCodexAttachmentStore reopened =
                new FileCodexAttachmentStore(temporaryDirectory);
        assertTrue(reopened.find(
                11L,
                "workspace-a",
                attachment.id()).isPresent());
    }
}
