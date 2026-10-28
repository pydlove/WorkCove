package com.pydlove.workcove.vault.domain.repository;

import com.pydlove.workcove.vault.domain.model.VaultChallenge;

import java.time.Instant;
import java.util.Optional;

public interface VaultChallengeRepository {

    VaultChallenge save(VaultChallenge challenge);

    Optional<VaultChallenge> findLatestActive(
            long ownerId,
            String purpose,
            Instant now);

    void markConsumed(long id, Instant consumedAt);

    void deleteExpiredChallenges(long ownerId, Instant now);
}
