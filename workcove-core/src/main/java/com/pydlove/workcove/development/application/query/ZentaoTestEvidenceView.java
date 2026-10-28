package com.pydlove.workcove.development.application.query;

import com.pydlove.workcove.development.domain.port.ZentaoTestEvidenceStore;

public record ZentaoTestEvidenceView(
        String filename,
        String contentType,
        long size) {

    public static ZentaoTestEvidenceView from(
            ZentaoTestEvidenceStore.EvidenceSummary evidence) {
        return new ZentaoTestEvidenceView(
                evidence.filename(),
                evidence.contentType(),
                evidence.size());
    }
}
