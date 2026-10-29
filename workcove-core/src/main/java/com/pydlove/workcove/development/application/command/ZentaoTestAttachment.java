package com.pydlove.workcove.development.application.command;

public record ZentaoTestAttachment(
        String filename,
        String contentType,
        byte[] content) {
}
