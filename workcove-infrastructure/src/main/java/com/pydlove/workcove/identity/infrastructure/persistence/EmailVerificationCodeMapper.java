package com.pydlove.workcove.identity.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;

@Mapper
public interface EmailVerificationCodeMapper {

    EmailVerificationCodeData findActive(
            @Param("email") String email,
            @Param("now") Instant now);

    Instant findLatestCreatedAt(@Param("email") String email);

    int countCreatedSince(
            @Param("email") String email,
            @Param("since") Instant since);

    int insert(EmailVerificationCodeData data);

    int update(EmailVerificationCodeData data);
}
