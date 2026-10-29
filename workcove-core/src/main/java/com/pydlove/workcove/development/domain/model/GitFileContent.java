package com.pydlove.workcove.development.domain.model;

import java.util.List;

public record GitFileContent(
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
}
