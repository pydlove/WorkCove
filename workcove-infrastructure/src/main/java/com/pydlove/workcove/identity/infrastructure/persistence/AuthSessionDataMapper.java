package com.pydlove.workcove.identity.infrastructure.persistence;

import com.pydlove.workcove.identity.domain.model.AuthSession;
import com.pydlove.workcove.identity.domain.model.SessionTokenHash;
import com.pydlove.workcove.identity.domain.model.UserId;

final class AuthSessionDataMapper {

    private AuthSessionDataMapper() {
    }

    static AuthSession toDomain(AuthSessionData data) {
        return AuthSession.restore(
                data.getId(),
                UserId.of(data.getUserId()),
                new SessionTokenHash(data.getTokenHash()),
                data.getExpiresAt(),
                data.isRemembered(),
                data.getCreatedAt());
    }

    static AuthSessionData toData(AuthSession session) {
        AuthSessionData data = new AuthSessionData();
        data.setId(session.getId());
        data.setUserId(session.getUserId().value());
        data.setTokenHash(session.getTokenHash().value());
        data.setExpiresAt(session.getExpiresAt());
        data.setRemembered(session.isRemembered());
        data.setCreatedAt(session.getCreatedAt());
        return data;
    }
}
