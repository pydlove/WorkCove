package com.pydlove.workcove.identity.domain.port;

import com.pydlove.workcove.identity.domain.model.PasswordHash;

public interface PasswordHasher {

    PasswordHash hash(String rawPassword);

    boolean matches(String rawPassword, PasswordHash encodedPassword);
}
