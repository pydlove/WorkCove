package com.pydlove.workcove.vault.domain.repository;

import com.pydlove.workcove.vault.domain.model.VaultCategory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface VaultCategoryRepository {

    List<VaultCategory> findCategoriesByOwnerIdAndWorkspaceId(
            long ownerId,
            String workspaceId);

    Optional<VaultCategory> findCategoryByIdAndOwnerIdAndWorkspaceId(
            long id,
            long ownerId,
            String workspaceId);

    Optional<VaultCategory> findCategoryByNameAndOwnerIdAndWorkspaceId(
            String name,
            long ownerId,
            String workspaceId);

    VaultCategory saveCategory(VaultCategory category);

    void renameCategory(
            long ownerId,
            String workspaceId,
            long id,
            String currentName,
            String nextName,
            Instant updatedAt);

    void deleteCategoryAndReassignEntries(
            long ownerId,
            String workspaceId,
            long id,
            String categoryName,
            String fallbackName,
            Instant updatedAt);
}
