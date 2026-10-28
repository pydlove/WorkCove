package com.pydlove.workcove.vault.domain.repository;

import com.pydlove.workcove.vault.domain.model.VaultUnlockSession;

import java.time.Instant;
import java.util.Optional;

public interface VaultUnlockSessionRepository {

    VaultUnlockSession save(VaultUnlockSession session);

    Optional<VaultUnlockSession> findActive(
            String tokenHash,
            Instant now);

    void deleteByTokenHash(String tokenHash);

    void deleteExpiredUnlockSessions(long ownerId, Instant now);
}
