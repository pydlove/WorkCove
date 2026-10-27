package com.pydlove.workcove.identity.domain.repository;

import com.pydlove.workcove.identity.domain.model.Email;
import com.pydlove.workcove.identity.domain.model.EmailVerificationCode;

import java.time.Instant;
import java.util.Optional;

public interface EmailVerificationCodeRepository {

    Optional<EmailVerificationCode> findActive(Email email, Instant now);

    Optional<Instant> findLatestCreatedAt(Email email);

    int countCreatedSince(Email email, Instant since);

    EmailVerificationCode save(EmailVerificationCode code);
}
