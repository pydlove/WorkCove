package com.pydlove.workcove.boot.config;

import com.pydlove.workcove.development.domain.port.CredentialCipher;
import com.pydlove.workcove.identity.domain.port.PasswordHasher;
import com.pydlove.workcove.identity.domain.port.SessionTokenCodec;
import com.pydlove.workcove.identity.domain.repository.UserRepository;
import com.pydlove.workcove.vault.application.VaultService;
import com.pydlove.workcove.vault.domain.port.VaultWebAuthnGateway;
import com.pydlove.workcove.vault.domain.repository.VaultChallengeRepository;
import com.pydlove.workcove.vault.domain.repository.VaultCategoryRepository;
import com.pydlove.workcove.vault.domain.repository.VaultEntryRepository;
import com.pydlove.workcove.vault.domain.repository.VaultPasskeyRepository;
import com.pydlove.workcove.vault.domain.repository.VaultUnlockSessionRepository;
import com.pydlove.workcove.vault.infrastructure.security.WebAuthn4jVaultGateway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class VaultConfiguration {

    @Bean
    public VaultWebAuthnGateway vaultWebAuthnGateway() {
        return new WebAuthn4jVaultGateway();
    }

    @Bean
    public VaultService vaultService(
            VaultEntryRepository entryRepository,
            VaultCategoryRepository categoryRepository,
            VaultPasskeyRepository passkeyRepository,
            VaultChallengeRepository challengeRepository,
            VaultUnlockSessionRepository unlockRepository,
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            SessionTokenCodec tokenCodec,
            CredentialCipher credentialCipher,
            VaultWebAuthnGateway webAuthnGateway,
            @Qualifier("clock") Clock clock,
            @Value("${workcove.vault.unlock-ttl:PT5M}") Duration unlockTtl) {
        return new VaultService(
                entryRepository,
                categoryRepository,
                passkeyRepository,
                challengeRepository,
                unlockRepository,
                userRepository,
                passwordHasher,
                tokenCodec,
                credentialCipher,
                webAuthnGateway,
                clock,
                unlockTtl);
    }
}
