package com.pydlove.workcove.development.infrastructure;

import com.pydlove.workcove.development.domain.port.SftpGateway;
import com.pydlove.workcove.development.domain.port.SshTerminalGateway;
import com.pydlove.workcove.shared.domain.DomainException;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SshjSftpGatewayIntegrationTest {

    private static final String USERNAME = "tester";
    private static final String PASSWORD = "test-password";

    private static SshServer sshServer;
    private static Path remoteRoot;

    private final SshjSftpGateway gateway = new SshjSftpGateway();

    @BeforeAll
    static void startSshServer() throws Exception {
        remoteRoot = Files.createTempDirectory("workcove-sftp-test-");
        Files.createDirectory(remoteRoot.resolve("docs"));
        Files.writeString(
                remoteRoot.resolve("hello.txt"),
                "hello from SFTP\n",
                StandardCharsets.UTF_8);

        Path hostKey = Files.createTempFile("workcove-sshd-", ".key");
        sshServer = SshServer.setUpDefaultServer();
        sshServer.setHost("127.0.0.1");
        sshServer.setPort(0);
        sshServer.setKeyPairProvider(
                new SimpleGeneratorHostKeyProvider(hostKey));
        sshServer.setPasswordAuthenticator(
                (username, password, session) ->
                        USERNAME.equals(username) && PASSWORD.equals(password));
        sshServer.setSubsystemFactories(
                List.of(new SftpSubsystemFactory.Builder().build()));
        sshServer.setFileSystemFactory(
                new VirtualFileSystemFactory(remoteRoot));
        sshServer.start();
    }

    @AfterAll
    static void stopSshServer() throws Exception {
        if (sshServer != null) sshServer.stop(true);
    }

    @Test
    void listsTransfersAndManagesRemoteFiles() throws Exception {
        SshTerminalGateway.Connection connection = connection();

        String hostKey;
        String fingerprint;
        try (SftpGateway.OpenedSftp opened = gateway.open(connection, null)) {
            hostKey = opened.hostKey().publicKey();
            fingerprint = opened.hostKey().fingerprint();
            List<SftpGateway.RemoteFile> files =
                    opened.session().list("/");
            assertTrue(files.stream().anyMatch(
                    (file) -> file.directory() && "docs".equals(file.name())));
            assertTrue(files.stream().anyMatch(
                    (file) -> !file.directory() && "hello.txt".equals(file.name())));
        }
        String expectedFingerprint = "SHA256:" + Base64.getEncoder()
                .withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256")
                        .digest(Base64.getDecoder().decode(hostKey)));
        assertEquals(expectedFingerprint, fingerprint);

        try (SftpGateway.OpenedSftp opened =
                     gateway.open(connection, trustedHostKey(hostKey))) {
            opened.session().createDirectory("/created");
            opened.session().upload(
                    "/",
                    "upload.txt",
                    new ByteArrayInputStream("uploaded\n".getBytes(StandardCharsets.UTF_8)),
                    9);
            assertTrue(Files.isDirectory(remoteRoot.resolve("created")));
            assertEquals(
                    "uploaded\n",
                    Files.readString(
                            remoteRoot.resolve("upload.txt"),
                            StandardCharsets.UTF_8));

            try (SftpGateway.DownloadedFile file =
                         opened.session().download("/hello.txt")) {
                assertArrayEquals(
                        "hello from SFTP\n".getBytes(StandardCharsets.UTF_8),
                        file.stream().readAllBytes());
            }

            opened.session().delete("/upload.txt", false);
            opened.session().delete("/created", true);
            assertTrue(Files.notExists(remoteRoot.resolve("upload.txt")));
            assertTrue(Files.notExists(remoteRoot.resolve("created")));
        }
    }

    @Test
    void rejectsAChangedHostKey() throws Exception {
        String encodedKey;
        try (SftpGateway.OpenedSftp opened = gateway.open(connection(), null)) {
            encodedKey = opened.hostKey().publicKey();
        }
        byte[] changed = Base64.getDecoder().decode(encodedKey);
        changed[changed.length - 1] ^= 1;
        SshTerminalGateway.HostKey changedHostKey =
                new SshTerminalGateway.HostKey(
                        "ssh-rsa",
                        "SHA256:changed",
                        Base64.getEncoder().encodeToString(changed));

        assertThrows(
                DomainException.class,
                () -> gateway.open(connection(), changedHostKey));
    }

    private static SshTerminalGateway.Connection connection() {
        return new SshTerminalGateway.Connection(
                "127.0.0.1",
                sshServer.getPort(),
                USERNAME,
                PASSWORD);
    }

    private static SshTerminalGateway.HostKey trustedHostKey(
            String publicKey) {
        return new SshTerminalGateway.HostKey(
                "ssh-rsa",
                "SHA256:trusted",
                publicKey);
    }
}
