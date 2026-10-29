package com.pydlove.workcove.vault.domain.repository;

import com.pydlove.workcove.vault.domain.model.VaultEntry;

import java.util.List;
import java.util.Optional;

public interface VaultEntryRepository {

    List<VaultEntry> findEntriesByOwnerIdAndWorkspaceId(
            long ownerId,
            String workspaceId);

    Optional<VaultEntry> findByIdAndOwnerIdAndWorkspaceId(
            long id,
            long ownerId,
            String workspaceId);

    VaultEntry save(VaultEntry entry);

    void deleteByIdAndOwnerIdAndWorkspaceId(
            long id,
            long ownerId,
            String workspaceId);

    void moveToWorkspace(
            long id,
            long ownerId,
            String sourceWorkspaceId,
            String targetWorkspaceId,
            String category,
            java.time.Instant updatedAt);
}
