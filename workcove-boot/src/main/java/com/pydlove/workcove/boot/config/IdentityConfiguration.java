package com.pydlove.workcove.boot.config;

import com.pydlove.workcove.identity.application.command.AuthService;
import com.pydlove.workcove.identity.application.command.EmailVerificationService;
import com.pydlove.workcove.identity.domain.port.EmailVerificationCodec;
import com.pydlove.workcove.identity.domain.port.PasswordHasher;
import com.pydlove.workcove.identity.domain.port.SessionTokenCodec;
import com.pydlove.workcove.identity.domain.port.VerificationEmailSender;
import com.pydlove.workcove.identity.domain.repository.AuthSessionRepository;
import com.pydlove.workcove.identity.domain.repository.EmailVerificationCodeRepository;
import com.pydlove.workcove.identity.domain.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class IdentityConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public EmailVerificationService emailVerificationService(
            UserRepository userRepository,
            EmailVerificationCodeRepository codeRepository,
            EmailVerificationCodec codec,
            VerificationEmailSender emailSender,
            Clock clock,
            @Value("${workcove.auth.email-code.ttl:PT5M}") Duration codeTtl,
            @Value("${workcove.auth.email-code.resend-cooldown:PT60S}") Duration resendCooldown,
            @Value("${workcove.auth.email-code.max-per-day:10}") int maxCodesPerDay) {
        return new EmailVerificationService(
                userRepository,
                codeRepository,
                codec,
                emailSender,
                clock,
                codeTtl,
                resendCooldown,
                maxCodesPerDay);
    }

    @Bean
    public AuthService authService(
            UserRepository userRepository,
            AuthSessionRepository sessionRepository,
            PasswordHasher passwordHasher,
            SessionTokenCodec tokenCodec,
            EmailVerificationService emailVerificationService,
            Clock clock,
            @Value("${workcove.auth.session-ttl:PT168H}") Duration sessionTtl,
            @Value("${workcove.auth.remembered-session-ttl:PT4320H}")
            Duration rememberedSessionTtl) {
        return new AuthService(
                userRepository,
                sessionRepository,
                passwordHasher,
                tokenCodec,
                emailVerificationService,
                clock,
                sessionTtl,
                rememberedSessionTtl);
    }
}
