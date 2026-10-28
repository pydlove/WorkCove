package com.pydlove.workcove.development.application;

import com.pydlove.workcove.development.domain.model.ManagedServer;
import com.pydlove.workcove.development.domain.port.CredentialCipher;
import com.pydlove.workcove.development.domain.port.SshTerminalGateway;
import com.pydlove.workcove.development.domain.repository.ManagedServerRepository;
import com.pydlove.workcove.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SshTerminalServiceTest {

    private static final long OWNER_ID = 10L;
    private static final long SERVER_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-10-27T00:00:00Z");
    private static final SshTerminalGateway.HostKey HOST_KEY =
            new SshTerminalGateway.HostKey(
                    "ssh-ed25519",
                    "SHA256:fingerprint",
                    "cHVibGljLWtleQ==");

    private final InMemoryRepository repository = new InMemoryRepository(
            ManagedServer.restore(
                    SERVER_ID,
                    OWNER_ID,
                    "default",
                    "server-a",
                    "10.0.0.1",
                    22,
                    "alice",
                    "encrypted:secret",
                    null,
                    null,
                    null,
                    NOW,
                    NOW));
    private final FakeTerminalGateway gateway = new FakeTerminalGateway();
    private final SshTerminalService service = new SshTerminalService(
            repository,
            new PassThroughCipher(),
            gateway,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void validatesTerminalDimensionsBeforeOpeningAConnection() {
        assertThrows(
                DomainException.class,
                () -> service.open(
                        OWNER_ID,
                        SERVER_ID,
                        10,
                        32,
                        listener()));
        assertThrows(
                DomainException.class,
                () -> service.open(
                        OWNER_ID,
                        SERVER_ID,
                        120,
                        300,
                        listener()));
        assertEquals(0, gateway.openCalls);
    }

    @Test
    void rejectsServersOwnedByAnotherUser() {
        assertThrows(
                DomainException.class,
                () -> service.open(
                        20L,
                        SERVER_ID,
                        120,
                        32,
                        listener()));
        assertEquals(0, gateway.openCalls);
    }

    @Test
    void trustsAndReusesTheObservedHostKey() {
        SshTerminalService.TerminalConnection first = service.open(
                OWNER_ID,
                SERVER_ID,
                120,
                32,
                listener());

        assertTrue(first.firstTrust());
        assertEquals(HOST_KEY.fingerprint(), first.hostKeyFingerprint());
        assertEquals("secret", gateway.lastConnection.password());

        SshTerminalService.TerminalConnection second = service.open(
                OWNER_ID,
                SERVER_ID,
                120,
                32,
                listener());

        assertFalse(second.firstTrust());
        assertNotNull(gateway.expectedHostKey);
        assertEquals(HOST_KEY.publicKey(), gateway.expectedHostKey.publicKey());
    }

    @Test
    void closesTheTerminalWhenTheHostKeyCannotBeSaved() {
        repository.failSave = true;

        assertThrows(
                IllegalStateException.class,
                () -> service.open(
                        OWNER_ID,
                        SERVER_ID,
                        120,
                        32,
                        listener()));
        assertTrue(gateway.lastTerminal.closed);
    }

    private static SshTerminalGateway.TerminalListener listener() {
        return new SshTerminalGateway.TerminalListener() {
            @Override
            public void onOutput(String data) {
            }

            @Override
            public void onClosed(String error) {
            }
        };
    }

    private static final class PassThroughCipher implements CredentialCipher {
        @Override
        public String encrypt(String rawValue) {
            return "encrypted:" + rawValue;
        }

        @Override
        public String decrypt(String encryptedValue) {
            return encryptedValue.replace("encrypted:", "");
        }
    }

    private static final class FakeTerminalGateway
            implements SshTerminalGateway {

        private int openCalls;
        private Connection lastConnection;
        private HostKey expectedHostKey;
        private FakeTerminal lastTerminal;

        @Override
        public OpenedTerminal open(
                Connection connection,
                HostKey expectedHostKey,
                int columns,
                int rows,
                TerminalListener listener) {
            openCalls += 1;
            lastConnection = connection;
            this.expectedHostKey = expectedHostKey;
            lastTerminal = new FakeTerminal();
            return new OpenedTerminal(lastTerminal, HOST_KEY);
        }
    }

    private static final class FakeTerminal implements SshTerminalGateway.Terminal {
        private boolean closed;

        @Override
        public void write(String data) {
        }

        @Override
        public void resize(int columns, int rows) {
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class InMemoryRepository
            implements ManagedServerRepository {

        private final List<ManagedServer> servers;
        private boolean failSave;

        private InMemoryRepository(ManagedServer server) {
            servers = new ArrayList<>(List.of(server));
        }

        @Override
        public List<ManagedServer> findByOwnerId(long ownerId) {
            return servers.stream()
                    .filter((server) -> server.getOwnerId() == ownerId)
                    .toList();
        }

        @Override
        public Optional<ManagedServer> findByIdAndOwnerId(
                long id,
                long ownerId) {
            return servers.stream()
                    .filter((server) -> server.getId() == id
                            && server.getOwnerId() == ownerId)
                    .findFirst();
        }

        @Override
        public ManagedServer save(ManagedServer server) {
            if (failSave) {
                throw new IllegalStateException("database unavailable");
            }
            servers.removeIf(
                    (item) -> item.getId().equals(server.getId()));
            servers.add(server);
            return server;
        }

        @Override
        public void deleteByIdAndOwnerId(long id, long ownerId) {
            servers.removeIf((server) -> server.getId() == id
                    && server.getOwnerId() == ownerId);
        }
    }
}
