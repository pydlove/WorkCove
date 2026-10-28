package com.pydlove.workcove.identity.infrastructure.persistence;

import com.pydlove.workcove.identity.domain.model.AuthSession;
import com.pydlove.workcove.identity.domain.model.SessionTokenHash;
import com.pydlove.workcove.identity.domain.repository.AuthSessionRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public class MyBatisAuthSessionRepository implements AuthSessionRepository {

    private final AuthSessionMapper mapper;

    public MyBatisAuthSessionRepository(AuthSessionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public AuthSession save(AuthSession session) {
        AuthSessionData data = AuthSessionDataMapper.toData(session);
        mapper.insert(data);
        return AuthSessionDataMapper.toDomain(data);
    }

    @Override
    public Optional<AuthSession> findActive(SessionTokenHash tokenHash, Instant now) {
        return Optional.ofNullable(mapper.findActive(tokenHash.value(), now))
                .map(AuthSessionDataMapper::toDomain);
    }

    @Override
    public void deleteByTokenHash(SessionTokenHash tokenHash) {
        mapper.deleteByTokenHash(tokenHash.value());
    }
}
