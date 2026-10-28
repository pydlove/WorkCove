package com.pydlove.workcove.development.application;

import com.pydlove.workcove.development.application.command.DeploymentExecutionCommand;
import com.pydlove.workcove.development.domain.model.DeploymentExecutionStatus;
import com.pydlove.workcove.development.domain.model.ManagedServer;
import com.pydlove.workcove.development.domain.port.CredentialCipher;
import com.pydlove.workcove.development.domain.port.RemoteDeploymentGateway;
import com.pydlove.workcove.development.domain.port.SshTerminalGateway;
import com.pydlove.workcove.development.domain.repository.ManagedServerRepository;
import com.pydlove.workcove.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeploymentExecutionServiceTest {

    private static final long OWNER_ID = 10L;
    private static final Instant NOW = Instant.parse("2026-10-27T00:00:00Z");
    private static final SshTerminalGateway.HostKey OBSERVED_HOST_KEY =
            new SshTerminalGateway.HostKey(
                    "ssh-ed25519",
                    "SHA256:test",
                    "public-key");

    private final InMemoryServerRepository repository =
            new InMemoryServerRepository();
    private final FakeDeploymentGateway gateway =
            new FakeDeploymentGateway();
    private final DeploymentExecutionService service =
            new DeploymentExecutionService(
                    repository,
                    new PassThroughCipher(),
                    gateway,
                    Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void executesScriptAndRemembersTrustedHostKey() {
        var result = service.execute(
                OWNER_ID,
                command("default"));

        assertEquals(DeploymentExecutionStatus.SUCCESS, result.status());
        assertEquals("deploy server-a\n", result.output());
        assertTrue(repository.saved);
        assertNull(gateway.expectedHostKey);
    }

    @Test
    void rejectsServerFromAnotherWorkspace() {
        assertThrows(
                DomainException.class,
                () -> service.execute(OWNER_ID, command("target")));
    }

    @Test
    void mapsTimeoutToDeploymentResult() {
        gateway.timeout = true;

        var result = service.execute(
                OWNER_ID,
                command("default"));

        assertEquals(DeploymentExecutionStatus.TIMEOUT, result.status());
        assertEquals(null, result.exitCode());
    }

    private static DeploymentExecutionCommand command(String workspaceId) {
        return new DeploymentExecutionCommand(
                workspaceId,
                1L,
                "/opt/workcove",
                "deploy.sh",
                "#!/usr/bin/env bash\necho deploy\n");
    }

    private static final class PassThroughCipher
            implements CredentialCipher {

        @Override
        public String encrypt(String rawValue) {
            return rawValue;
        }

        @Override
        public String decrypt(String encryptedValue) {
            return encryptedValue;
        }
    }

    private static final class FakeDeploymentGateway
            implements RemoteDeploymentGateway {

        private boolean timeout;
        private SshTerminalGateway.HostKey expectedHostKey;

        @Override
        public Result execute(
                SshTerminalGateway.Connection connection,
                SshTerminalGateway.HostKey expectedHostKey,
                String remoteDirectory,
                String scriptFilename,
                String scriptContent,
                Duration timeout) {
            this.expectedHostKey = expectedHostKey;
            return new Result(
                    this.timeout,
                    this.timeout ? null : 0,
                    "deploy server-a\n",
                    OBSERVED_HOST_KEY);
        }
    }

    private static final class InMemoryServerRepository
            implements ManagedServerRepository {

        private ManagedServer server = ManagedServer.restore(
                1L,
                OWNER_ID,
                "default",
                "server-a",
                "10.0.0.1",
                22,
                "alice",
                "password",
                null,
                null,
                null,
                NOW,
                NOW);
        private boolean saved;

        @Override
        public List<ManagedServer> findByOwnerId(long ownerId) {
            return server.getOwnerId() == ownerId
                    ? List.of(server)
                    : List.of();
        }

        @Override
        public Optional<ManagedServer> findByIdAndOwnerId(
                long id,
                long ownerId) {
            return server.getId() == id && server.getOwnerId() == ownerId
                    ? Optional.of(server)
                    : Optional.empty();
        }

        @Override
        public ManagedServer save(ManagedServer server) {
            this.server = server;
            saved = true;
            return server;
        }

        @Override
        public void deleteByIdAndOwnerId(long id, long ownerId) {
        }
    }
}
