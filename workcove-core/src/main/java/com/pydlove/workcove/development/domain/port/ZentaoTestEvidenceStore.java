package com.pydlove.workcove.development.domain.port;

import java.util.List;
import java.util.Optional;

public interface ZentaoTestEvidenceStore {

    PreparedRun prepare(
            long ownerId,
            String workspaceId,
            long bugId,
            String runId);

    List<EvidenceSummary> list(
            long ownerId,
            String workspaceId,
            long bugId,
            String runId);

    Optional<EvidenceFile> find(
            long ownerId,
            String workspaceId,
            long bugId,
            String runId,
            String filename);

    record PreparedRun(String directory) {
    }

    record EvidenceSummary(
            String filename,
            String contentType,
            long size) {
    }

    record EvidenceFile(
            String filename,
            String contentType,
            long size,
            byte[] content) {
    }
}
