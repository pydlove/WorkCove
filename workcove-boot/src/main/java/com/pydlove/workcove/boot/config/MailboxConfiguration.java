package com.pydlove.workcove.boot.config;

import com.pydlove.workcove.development.domain.port.CredentialCipher;
import com.pydlove.workcove.mail.application.MailService;
import com.pydlove.workcove.mail.domain.port.MailGateway;
import com.pydlove.workcove.mail.domain.repository.MailAccountRepository;
import com.pydlove.workcove.mail.domain.repository.MailCacheRepository;
import com.pydlove.workcove.mail.domain.repository.MailRuleRepository;
import com.pydlove.workcove.mail.infrastructure.JakartaMailGateway;
import com.pydlove.workcove.identity.infrastructure.mail.WorkCoveMailTls;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLSocketFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

@Configuration
public class MailboxConfiguration {

    @Bean
    public MailGateway mailGateway(
            @Value("${workcove.mail.trust-store:${user.home}/.workcove/mail-truststore.p12}")
            String trustStorePath,
            @Value("${workcove.mail.trust-store-password:workcove-mail-trust}")
            String trustStorePassword) {
        SSLSocketFactory socketFactory = null;
        Path resolvedTrustStore = resolveTrustStore(trustStorePath);
        if (resolvedTrustStore != null) {
            socketFactory = WorkCoveMailTls.socketFactory(
                    resolvedTrustStore.toString(),
                    trustStorePassword);
        }
        return new JakartaMailGateway(true, socketFactory);
    }

    private static Path resolveTrustStore(String configuredPath) {
        if (configuredPath != null && !configuredPath.isBlank()) {
            Path configured = Path.of(configuredPath);
            if (Files.isRegularFile(configured)) {
                return configured;
            }
        }
        String userHome = System.getProperty("user.home", "");
        if (userHome.isBlank()) {
            return null;
        }
        Path shared = Path.of(
                userHome,
                ".workcove",
                "mail-truststore.p12");
        return Files.isRegularFile(shared) ? shared : null;
    }

    @Bean
    public MailService mailService(
            MailAccountRepository repository,
            MailCacheRepository cacheRepository,
            MailRuleRepository ruleRepository,
            MailGateway gateway,
            CredentialCipher credentialCipher,
            @Qualifier("clock") Clock clock) {
        return new MailService(
                repository,
                cacheRepository,
                ruleRepository,
                gateway,
                credentialCipher,
                clock);
    }
}
