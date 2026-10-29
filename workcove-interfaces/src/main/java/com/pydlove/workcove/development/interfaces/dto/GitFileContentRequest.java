package com.pydlove.workcove.development.interfaces.dto;

public record GitFileContentRequest(
        String path,
        String revision,
        String filePath,
        Integer line
) {
}
