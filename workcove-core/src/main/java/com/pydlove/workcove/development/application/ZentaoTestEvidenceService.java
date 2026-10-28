package com.pydlove.workcove.development.application;

import com.pydlove.workcove.development.application.query.ZentaoTestEvidenceMediaView;
import com.pydlove.workcove.development.application.query.ZentaoTestEvidenceView;
import com.pydlove.workcove.development.application.query.ZentaoTestRunView;
import com.pydlove.workcove.development.domain.model.ZentaoBug;
import com.pydlove.workcove.development.domain.port.ZentaoTestEvidenceStore;
import com.pydlove.workcove.development.domain.repository.ZentaoRepository;
import com.pydlove.workcove.shared.domain.DomainException;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ZentaoTestEvidenceService {

    private final ZentaoRepository repository;
    private final ZentaoTestEvidenceStore evidenceStore;

    public ZentaoTestEvidenceService(
            ZentaoRepository repository,
            ZentaoTestEvidenceStore evidenceStore) {
        this.repository = Objects.requireNonNull(repository);
        this.evidenceStore = Objects.requireNonNull(evidenceStore);
    }

    public ZentaoTestRunView prepare(
            long ownerId,
            String workspaceId,
            long bugId) {
        requireBug(ownerId, workspaceId, bugId);
        String runId = UUID.randomUUID().toString();
        ZentaoTestEvidenceStore.PreparedRun run =
                evidenceStore.prepare(
                        ownerId,
                        workspaceId,
                        bugId,
                        runId);
        return new ZentaoTestRunView(runId, run.directory());
    }

    public List<ZentaoTestEvidenceView> evidence(
            long ownerId,
            String workspaceId,
            long bugId,
            String runId) {
        requireBug(ownerId, workspaceId, bugId);
        return evidenceStore.list(
                        ownerId,
                        workspaceId,
                        bugId,
                        runId)
                .stream()
                .map(ZentaoTestEvidenceView::from)
                .toList();
    }

    public ZentaoTestEvidenceMediaView read(
            long ownerId,
            String workspaceId,
            long bugId,
            String runId,
            String filename) {
        requireBug(ownerId, workspaceId, bugId);
        return evidenceStore.find(
                        ownerId,
                        workspaceId,
                        bugId,
                        runId,
                        filename)
                .map(file -> new ZentaoTestEvidenceMediaView(
                        file.filename(),
                        file.contentType(),
                        file.content()))
                .orElseThrow(() -> new DomainException(
                        "测试截图不存在或无权访问"));
    }

    private ZentaoBug requireBug(
            long ownerId,
            String workspaceId,
            long bugId) {
        ZentaoBug bug = repository.findBug(ownerId, workspaceId, bugId)
                .orElseThrow(() -> new DomainException(
                        "Bug 不存在或无权访问"));
        if (bug.localDeleted()) {
            throw new DomainException("Bug 已从 8小时工作台移除");
        }
        return bug;
    }
}
