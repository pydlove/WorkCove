package com.pydlove.workcove.development.application;

import com.pydlove.workcove.development.domain.model.ManagedServer;
import com.pydlove.workcove.development.domain.model.ServerTool;
import com.pydlove.workcove.development.domain.port.CredentialCipher;
import com.pydlove.workcove.development.domain.port.ServerLauncher;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

class ManagedServerServiceTest {

    private static final long OWNER_ID = 10L;
    private static final Instant NOW = Instant.parse("2026-10-27T00:00:00Z");

    private final ManagedServerRepository repository = new InMemoryRepository(
            ManagedServer.restore(
                    1L,
                    OWNER_ID,
                    "default",
                    "server-a",
                    "10.0.0.1",
                    22,
                    "alice",
                    "encrypted",
                    null,
                    null,
                    null,
                    NOW,
                    NOW));

    private final ManagedServerService service = new ManagedServerService(
            repository,
            new PassThroughCipher(),
            new NoopLauncher(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void listsOnlyServersOwnedByCurrentUser() {
        assertEquals(1, service.list(OWNER_ID, "default").size());
        assertEquals(0, service.list(20L, "default").size());
        assertEquals(0, service.list(OWNER_ID, "another-workspace").size());
    }

    @Test
    void rejectsOpeningAnotherUsersServer() {
        assertThrows(
                DomainException.class,
                () -> service.open(
                        20L,
                        "default",
                        1L,
                        ServerTool.TERMIUS));
    }

    @Test
    void rejectsOpeningServerFromAnotherWorkspace() {
        assertThrows(
                DomainException.class,
                () -> service.open(
                        OWNER_ID,
                        "another-workspace",
                        1L,
                        ServerTool.TERMIUS));
        assertThrows(
                DomainException.class,
                () -> service.delete(
                        OWNER_ID,
                        "another-workspace",
                        1L));
    }

    @Test
    void copiesServerIntoAnotherWorkspace() {
        var copied = service.copyToWorkspace(
                OWNER_ID,
                "default",
                1L,
                "target");

        assertEquals("target", copied.workspaceId());
        assertEquals("server-a 副本", copied.name());
    }

    @Test
    void movesServerIntoAnotherWorkspace() {
        var moved = service.moveToWorkspace(
                OWNER_ID,
                "default",
                1L,
                "target");

        assertEquals("target", moved.workspaceId());
        assertEquals("server-a", moved.name());
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

    private static final class NoopLauncher implements ServerLauncher {
        @Override
        public LaunchResult open(ServerConnection connection, ServerTool tool) {
            return new LaunchResult(false);
        }
    }

    private static final class InMemoryRepository implements ManagedServerRepository {
        private final List<ManagedServer> servers;
        private long nextId = 100L;

        private InMemoryRepository(ManagedServer server) {
            this.servers = new ArrayList<>(List.of(server));
        }

        @Override
        public List<ManagedServer> findByOwnerIdAndWorkspaceId(
                long ownerId,
                String workspaceId) {
            return servers.stream()
                    .filter((server) -> server.getOwnerId() == ownerId)
                    .filter((server) ->
                            server.getWorkspaceId().equals(workspaceId))
                    .toList();
        }

        @Override
        public Optional<ManagedServer> findByIdAndOwnerIdAndWorkspaceId(
                long id,
                long ownerId,
                String workspaceId) {
            return servers.stream()
                    .filter((server) -> server.getId() == id
                            && server.getOwnerId() == ownerId
                            && server.getWorkspaceId().equals(workspaceId))
                    .findFirst();
        }

        @Override
        public ManagedServer save(ManagedServer server) {
            ManagedServer saved = server.getId() == null
                    ? ManagedServer.restore(
                            nextId++,
                            server.getOwnerId(),
                            server.getWorkspaceId(),
                            server.getName(),
                            server.getHost(),
                            server.getPort(),
                            server.getUsername(),
                            server.getPasswordCiphertext(),
                            server.getHostKeyAlgorithm(),
                            server.getHostKeyFingerprint(),
                            server.getHostKeyPublicKey(),
                            server.getCreatedAt(),
                            server.getUpdatedAt())
                    : server;
            servers.removeIf((item) -> item.getId().equals(saved.getId()));
            servers.add(saved);
            return saved;
        }

        @Override
        public void deleteByIdAndOwnerIdAndWorkspaceId(
                long id,
                long ownerId,
                String workspaceId) {
            servers.removeIf((server) -> server.getId() == id
                    && server.getOwnerId() == ownerId
                    && server.getWorkspaceId().equals(workspaceId));
        }
    }
}
