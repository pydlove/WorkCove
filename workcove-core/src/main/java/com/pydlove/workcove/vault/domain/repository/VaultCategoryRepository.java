package com.pydlove.workcove.vault.domain.repository;

import com.pydlove.workcove.vault.domain.model.VaultCategory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface VaultCategoryRepository {

    List<VaultCategory> findCategoriesByOwnerId(long ownerId);

    Optional<VaultCategory> findCategoryByIdAndOwnerId(
            long id,
            long ownerId);

    Optional<VaultCategory> findCategoryByNameAndOwnerId(
            String name,
            long ownerId);

    VaultCategory saveCategory(VaultCategory category);

    void renameCategory(
            long ownerId,
            long id,
            String currentName,
            String nextName,
            Instant updatedAt);

    void deleteCategoryAndReassignEntries(
            long ownerId,
            long id,
            String categoryName,
            String fallbackName,
            Instant updatedAt);
}
