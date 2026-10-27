package com.pydlove.workcove.identity.infrastructure.persistence;

import com.pydlove.workcove.identity.domain.model.Email;
import com.pydlove.workcove.identity.domain.model.EmailVerificationCode;
import com.pydlove.workcove.identity.domain.model.EmailVerificationCodeHash;

final class EmailVerificationCodeDataMapper {

    private EmailVerificationCodeDataMapper() {
    }

    static EmailVerificationCode toDomain(EmailVerificationCodeData data) {
        return EmailVerificationCode.restore(
                data.getId(),
                Email.of(data.getEmail()),
                new EmailVerificationCodeHash(data.getCodeHash()),
                data.getExpiresAt(),
                data.getConsumedAt(),
                data.getFailedAttempts(),
                data.getCreatedAt());
    }

    static EmailVerificationCodeData toData(EmailVerificationCode code) {
        EmailVerificationCodeData data = new EmailVerificationCodeData();
        data.setId(code.getId());
        data.setEmail(code.getEmail().value());
        data.setCodeHash(code.getCodeHash().value());
        data.setExpiresAt(code.getExpiresAt());
        data.setConsumedAt(code.getConsumedAt());
        data.setFailedAttempts(code.getFailedAttempts());
        data.setCreatedAt(code.getCreatedAt());
        return data;
    }
}
