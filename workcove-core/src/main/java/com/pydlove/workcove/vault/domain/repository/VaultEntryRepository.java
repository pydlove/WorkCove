package com.pydlove.workcove.vault.domain.repository;

import com.pydlove.workcove.vault.domain.model.VaultEntry;

import java.util.List;
import java.util.Optional;

public interface VaultEntryRepository {

    List<VaultEntry> findEntriesByOwnerId(long ownerId);

    Optional<VaultEntry> findByIdAndOwnerId(long id, long ownerId);

    VaultEntry save(VaultEntry entry);

    void deleteByIdAndOwnerId(long id, long ownerId);
}
