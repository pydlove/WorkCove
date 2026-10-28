package com.pydlove.workcove.vault.domain.repository;

import com.pydlove.workcove.vault.domain.model.VaultPasskey;

import java.util.List;
import java.util.Optional;

public interface VaultPasskeyRepository {

    List<VaultPasskey> findPasskeysByOwnerId(long ownerId);

    Optional<VaultPasskey> findByCredentialIdAndOwnerId(
            String credentialId,
            long ownerId);

    VaultPasskey save(VaultPasskey passkey);

    void updateUsage(String credentialId, long ownerId, long signCount,
                     java.time.Instant usedAt);
}
