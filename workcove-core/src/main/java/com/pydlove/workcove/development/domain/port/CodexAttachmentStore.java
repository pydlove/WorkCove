package com.pydlove.workcove.development.domain.port;

import com.pydlove.workcove.development.domain.model.CodexAttachment;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public interface CodexAttachmentStore {

    CodexAttachment store(
            long ownerId,
            String name,
            String contentType,
            long size,
            InputStream content) throws IOException;

    Optional<CodexAttachment> find(long ownerId, String attachmentId);
}
