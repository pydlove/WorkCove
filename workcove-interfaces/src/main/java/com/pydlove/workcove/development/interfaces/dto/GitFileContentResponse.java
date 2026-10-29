package com.pydlove.workcove.development.interfaces.dto;

import com.pydlove.workcove.development.domain.model.GitFileContent;

import java.util.List;

public record GitFileContentResponse(
        String revision,
        String filePath,
        int requestedLine,
        int startLine,
        int endLine,
        int totalLines,
        List<String> lines,
        boolean truncatedBefore,
        boolean truncatedAfter
) {
    public static GitFileContentResponse from(
            GitFileContent content) {
        return new GitFileContentResponse(
                content.revision(),
                content.filePath(),
                content.requestedLine(),
                content.startLine(),
                content.endLine(),
                content.totalLines(),
                content.lines(),
                content.truncatedBefore(),
                content.truncatedAfter());
    }
}
