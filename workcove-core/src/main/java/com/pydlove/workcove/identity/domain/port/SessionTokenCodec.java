package com.pydlove.workcove.identity.domain.port;

import com.pydlove.workcove.identity.domain.model.SessionTokenHash;

public interface SessionTokenCodec {

    String generateRawToken();

    SessionTokenHash hash(String rawToken);
}
