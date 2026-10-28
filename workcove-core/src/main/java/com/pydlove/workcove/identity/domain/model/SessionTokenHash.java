package com.pydlove.workcove.identity.domain.model;

import com.pydlove.workcove.shared.domain.DomainException;

public record SessionTokenHash(String value) {

    public SessionTokenHash {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new DomainException("会话令牌摘要格式无效");
        }
    }
}
