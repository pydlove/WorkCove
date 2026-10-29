package com.pydlove.workcove.development.application;

import com.pydlove.workcove.development.domain.model.CodexAttachment;
import com.pydlove.workcove.development.domain.port.CodexAttachmentStore;
import com.pydlove.workcove.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CodexAttachmentServiceTest {

    @Test
    void storesSanitizedMetadataAndResolvesOnlyForOwner() {
        CapturingStore store = new CapturingStore();
        CodexAttachmentService service = new CodexAttachmentService(store);

        CodexAttachment attachment = service.upload(
                7L,
                "workspace-a",
                "../unsafe\nname.png",
                "IMAGE/PNG",
                3,
                new ByteArrayInputStream(new byte[]{1, 2, 3}));

        assertEquals("unsafe name.png", attachment.name());
        assertEquals("image/png", attachment.contentType());
        assertEquals(
                List.of(attachment),
                service.resolve(
                        7L,
                        "workspace-a",
                        List.of(attachment.id())));
        assertThrows(DomainException.class, () ->
                service.resolve(
                        8L,
                        "workspace-a",
                        List.of(attachment.id())));
        assertThrows(DomainException.class, () ->
                service.resolve(
                        7L,
                        "workspace-b",
                        List.of(attachment.id())));
    }

    @Test
    void rejectsOversizedAttachmentsBeforeWriting() {
        CapturingStore store = new CapturingStore();
        CodexAttachmentService service = new CodexAttachmentService(store);

        assertThrows(DomainException.class, () ->
                service.upload(
                        7L,
                        "workspace-a",
                        "large.bin",
                        "application/octet-stream",
                        CodexAttachmentService.MAX_ATTACHMENT_BYTES + 1,
                        new ByteArrayInputStream(new byte[]{1})));
    }

    private static final class CapturingStore implements CodexAttachmentStore {

        private final Map<String, CodexAttachment> attachments = new HashMap<>();

        @Override
        public CodexAttachment store(
                long ownerId,
                String workspaceId,
                String name,
                String contentType,
                long size,
                InputStream content) throws IOException {
            content.readAllBytes();
            String id = "attachment-" + attachments.size();
            CodexAttachment attachment = new CodexAttachment(
                    id,
                    workspaceId,
                    name,
                    contentType,
                    size,
                    Path.of("/tmp", id));
            attachments.put(
                    ownerId + ":" + workspaceId + ":" + id,
                    attachment);
            return attachment;
        }

        @Override
        public Optional<CodexAttachment> find(
                long ownerId,
                String workspaceId,
                String attachmentId) {
            return Optional.ofNullable(
                    attachments.get(
                            ownerId + ":" + workspaceId + ":" + attachmentId));
        }
    }
}
