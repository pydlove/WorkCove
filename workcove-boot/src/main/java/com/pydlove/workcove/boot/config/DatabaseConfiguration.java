package com.pydlove.workcove.boot.config;

import com.pydlove.workcove.database.application.DatabaseConnectionService;
import com.pydlove.workcove.database.domain.port.DatabaseQueryGateway;
import com.pydlove.workcove.database.domain.repository.DatabaseConnectionRepository;
import com.pydlove.workcove.development.domain.port.CredentialCipher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class DatabaseConfiguration {

    @Bean
    public DatabaseConnectionService databaseConnectionService(
            DatabaseConnectionRepository repository,
            DatabaseQueryGateway queryGateway,
            CredentialCipher credentialCipher,
            @Qualifier("clock") Clock clock) {
        return new DatabaseConnectionService(
                repository,
                queryGateway,
                credentialCipher,
                clock);
    }
}
