package com.pydlove.workcove.identity.domain.repository;

import com.pydlove.workcove.identity.domain.model.AuthSession;
import com.pydlove.workcove.identity.domain.model.SessionTokenHash;

import java.time.Instant;
import java.util.Optional;

public interface AuthSessionRepository {

    AuthSession save(AuthSession session);

    Optional<AuthSession> findActive(SessionTokenHash tokenHash, Instant now);

    void deleteByTokenHash(SessionTokenHash tokenHash);
}
