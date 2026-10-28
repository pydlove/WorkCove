package com.pydlove.workcove.identity.infrastructure.persistence;

import com.pydlove.workcove.identity.domain.model.Email;
import com.pydlove.workcove.identity.domain.model.EmailVerificationCode;
import com.pydlove.workcove.identity.domain.repository.EmailVerificationCodeRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public class MyBatisEmailVerificationCodeRepository
        implements EmailVerificationCodeRepository {

    private final EmailVerificationCodeMapper mapper;

    public MyBatisEmailVerificationCodeRepository(EmailVerificationCodeMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<EmailVerificationCode> findActive(Email email, Instant now) {
        return Optional.ofNullable(mapper.findActive(email.value(), now))
                .map(EmailVerificationCodeDataMapper::toDomain);
    }

    @Override
    public Optional<Instant> findLatestCreatedAt(Email email) {
        return Optional.ofNullable(mapper.findLatestCreatedAt(email.value()));
    }

    @Override
    public int countCreatedSince(Email email, Instant since) {
        return mapper.countCreatedSince(email.value(), since);
    }

    @Override
    public EmailVerificationCode save(EmailVerificationCode code) {
        EmailVerificationCodeData data = EmailVerificationCodeDataMapper.toData(code);
        if (data.getId() == null) {
            mapper.insert(data);
        } else {
            mapper.update(data);
        }
        return EmailVerificationCodeDataMapper.toDomain(data);
    }
}
