package com.pydlove.workcove.development.domain.model;

import java.nio.file.Path;

public record CodexAttachment(
        String id,
        String workspaceId,
        String name,
        String contentType,
        long size,
        Path path) {

    public boolean image() {
        return contentType != null && contentType.startsWith("image/");
    }
}
