package com.pydlove.workcove.identity.domain.model;

import com.pydlove.workcove.shared.domain.DomainException;

/** 只允许持久化带算法参数的密码摘要，不接受明文密码。 */
public record PasswordHash(String value) {

    public PasswordHash {
        if (value == null || value.isBlank()) {
            throw new DomainException("密码摘要不能为空");
        }
        if (value.length() > 255) {
            throw new DomainException("密码摘要格式无效");
        }
    }
}
