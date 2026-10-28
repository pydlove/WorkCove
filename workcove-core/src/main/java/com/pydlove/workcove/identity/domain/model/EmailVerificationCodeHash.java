package com.pydlove.workcove.identity.domain.model;

import com.pydlove.workcove.shared.domain.DomainException;

public record EmailVerificationCodeHash(String value) {

    public EmailVerificationCodeHash {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new DomainException("邮箱验证码摘要格式无效");
        }
    }
}
