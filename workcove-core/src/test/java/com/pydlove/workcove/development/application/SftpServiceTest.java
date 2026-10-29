package com.pydlove.workcove.development.application;

import com.pydlove.workcove.development.domain.model.ManagedServer;
import com.pydlove.workcove.development.domain.port.CredentialCipher;
import com.pydlove.workcove.development.domain.port.SftpGateway;
import com.pydlove.workcove.development.domain.port.SshTerminalGateway;
import com.pydlove.workcove.development.domain.repository.ManagedServerRepository;
import com.pydlove.workcove.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SftpServiceTest {

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
    private final FakeSftpGateway gateway = new FakeSftpGateway();
    private final SftpService service = new SftpService(
            repository,
            new PassThroughCipher(),
            gateway,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void rejectsServersOwnedByAnotherUser() {
        assertThrows(
                DomainException.class,
                () -> service.list(20L, "default", SERVER_ID, "/"));
        assertEquals(0, gateway.openCalls);
    }

    @Test
    void rejectsServersFromAnotherWorkspace() {
        assertThrows(
                DomainException.class,
                () -> service.list(
                        OWNER_ID,
                        "workspace-b",
                        SERVER_ID,
                        "/"));
        assertEquals(0, gateway.openCalls);
    }

    @Test
    void recordsTheHostKeyOnFirstSuccessfulConnection() {
        gateway.remoteSize = 4;

        service.list(OWNER_ID, "default", SERVER_ID, "/");

        ManagedServer saved = repository.findByIdAndOwnerIdAndWorkspaceId(
                SERVER_ID,
                OWNER_ID,
                "default").orElseThrow();
        assertEquals(HOST_KEY.algorithm(), saved.getHostKeyAlgorithm());
        assertEquals(HOST_KEY.fingerprint(), saved.getHostKeyFingerprint());
        assertEquals(HOST_KEY.publicKey(), saved.getHostKeyPublicKey());
        assertEquals("secret", gateway.lastConnection.password());
    }

    @Test
    void rejectsOversizedDownloadsBeforeReadingTheFile() {
        gateway.remoteSize = 200L * 1024 * 1024 + 1;

        assertThrows(
                DomainException.class,
                () -> service.download(
                        OWNER_ID,
                        "default",
                        SERVER_ID,
                        "/large.zip"));
        assertEquals(0, gateway.lastSession.downloadCalls);
        assertTrue(gateway.lastSession.closed);
    }

    @Test
    void reportsSuccessfulWorkEvenWhenCleanupFails() throws Exception {
        gateway.closeFailure = true;

        service.upload(
                OWNER_ID,
                "default",
                SERVER_ID,
                "/",
                "notes.txt",
                new ByteArrayInputStream(
                        "notes".getBytes(StandardCharsets.UTF_8)),
                5);

        assertTrue(gateway.lastSession.uploaded);
    }

    @Test
    void closesTheSessionWhenTheHostKeyCannotBeSaved() {
        repository.failSave = true;

        assertThrows(
                IllegalStateException.class,
                () -> service.list(OWNER_ID, "default", SERVER_ID, "/"));
        assertTrue(gateway.lastSession.closed);
    }

    @Test
    void validatesRemotePathsAndUploadNames() {
        assertThrows(
                DomainException.class,
                () -> service.list(
                        OWNER_ID,
                        "default",
                        SERVER_ID,
                        "relative"));
        assertThrows(
                DomainException.class,
                () -> service.upload(
                        OWNER_ID,
                        "default",
                        SERVER_ID,
                        "/",
                        "..",
                        InputStream.nullInputStream(),
                        0));
        assertThrows(
                DomainException.class,
                () -> service.delete(
                        OWNER_ID,
                        "default",
                        SERVER_ID,
                        "/",
                        true));
        assertEquals(0, gateway.openCalls);
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

    private static final class FakeSftpGateway implements SftpGateway {

        private int openCalls;
        private long remoteSize;
        private boolean closeFailure;
        private SshTerminalGateway.Connection lastConnection;
        private FakeSftpSession lastSession;

        @Override
        public OpenedSftp open(
                SshTerminalGateway.Connection connection,
                SshTerminalGateway.HostKey expectedHostKey) {
            openCalls += 1;
            lastConnection = connection;
            lastSession = new FakeSftpSession(this);
            return new OpenedSftp(lastSession, HOST_KEY);
        }
    }

    private static final class FakeSftpSession implements SftpGateway.SftpSession {

        private final FakeSftpGateway gateway;
        private int downloadCalls;
        private boolean uploaded;
        private boolean closed;

        private FakeSftpSession(FakeSftpGateway gateway) {
            this.gateway = gateway;
        }

        @Override
        public List<SftpGateway.RemoteFile> list(String path) {
            return List.of();
        }

        @Override
        public long size(String path) {
            return gateway.remoteSize;
        }

        @Override
        public SftpGateway.DownloadedFile download(String path) {
            downloadCalls += 1;
            throw new AssertionError("oversized file must not be downloaded");
        }

        @Override
        public void upload(
                String directory,
                String filename,
                InputStream input,
                long size) throws IOException {
            input.readAllBytes();
            uploaded = true;
        }

        @Override
        public void createDirectory(String path) {
        }

        @Override
        public void delete(String path, boolean directory) {
        }

        @Override
        public void close() throws IOException {
            closed = true;
            if (gateway.closeFailure) {
                throw new IOException("close failed after operation");
            }
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
            if (failSave) {
                throw new IllegalStateException("database unavailable");
            }
            servers.removeIf(
                    (item) -> item.getId().equals(server.getId()));
            servers.add(server);
            return server;
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
