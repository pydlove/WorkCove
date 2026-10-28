package com.pydlove.workcove.identity.domain.port;

import com.pydlove.workcove.identity.domain.model.EmailVerificationCodeHash;

public interface EmailVerificationCodec {

    String generateCode();

    EmailVerificationCodeHash hash(String rawCode);
}
