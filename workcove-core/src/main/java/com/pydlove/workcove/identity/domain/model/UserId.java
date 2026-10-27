package com.pydlove.workcove.identity.domain.model;

import com.pydlove.workcove.shared.domain.DomainException;

public record UserId(long value) {

    public UserId {
        if (value <= 0) {
            throw new DomainException("用户 ID 必须大于零");
        }
    }

    public static UserId of(long value) {
        return new UserId(value);
    }
}
