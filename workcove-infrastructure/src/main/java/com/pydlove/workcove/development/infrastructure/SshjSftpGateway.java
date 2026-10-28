package com.pydlove.workcove.development.infrastructure;

import com.pydlove.workcove.development.domain.port.SftpGateway;
import com.pydlove.workcove.development.domain.port.SshTerminalGateway;
import com.pydlove.workcove.shared.domain.DomainException;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public final class SshjSftpGateway implements SftpGateway {

    private final SshjConnector connector = new SshjConnector();

    @Override
    public OpenedSftp open(
            SshTerminalGateway.Connection connection,
            SshTerminalGateway.HostKey expectedHostKey) throws IOException {
        SshjConnector.ConnectedClient connected =
                connector.connect(connection, expectedHostKey);
        try {
            return new OpenedSftp(
                    new SshjSftpSession(connected.client()),
                    connected.hostKey());
        } catch (IOException exception) {
            SshjConnector.closeQuietly(connected.client());
            throw exception;
        }
    }

    private static final class SshjSftpSession implements SftpSession {

        private final SSHClient client;
        private final SFTPClient sftp;

        private SshjSftpSession(SSHClient client) throws IOException {
            this.client = client;
            this.sftp = client.newSFTPClient();
        }

        @Override
        public List<RemoteFile> list(String path) throws IOException {
            return sftp.ls(path).stream()
                    .filter((file) -> !".".equals(file.getName())
                            && !"..".equals(file.getName()))
                    .map((file) -> toRemoteFile(path, file))
                    .sorted(Comparator
                            .comparing(RemoteFile::directory)
                            .reversed()
                            .thenComparing(RemoteFile::name,
                                    String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }

        @Override
        public long size(String path) throws IOException {
            return sftp.size(path);
        }

        @Override
        public DownloadedFile download(String path) throws IOException {
            long size = sftp.size(path);
            String name = Path.of(path).getFileName().toString();
            String suffix = name.contains(".")
                    ? name.substring(name.lastIndexOf('.'))
                    : ".download";
            Path temporary = Files.createTempFile("workcove-sftp-", suffix);
            try {
                sftp.get(path, temporary.toString());
                InputStream stream = Files.newInputStream(
                        temporary,
                        StandardOpenOption.DELETE_ON_CLOSE);
                return new DownloadedFile() {
                    @Override
                    public String name() {
                        return name;
                    }

                    @Override
                    public long size() {
                        return size;
                    }

                    @Override
                    public InputStream stream() {
                        return stream;
                    }

                    @Override
                    public void close() throws IOException {
                        stream.close();
                    }
                };
            } catch (IOException exception) {
                Files.deleteIfExists(temporary);
                throw exception;
            }
        }

        @Override
        public void upload(
                String directory,
                String filename,
                InputStream input,
                long size) throws IOException {
            Path temporary = Files.createTempFile("workcove-upload-", ".tmp");
            try {
                Files.copy(input, temporary,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                String remotePath = join(directory, filename);
                sftp.put(temporary.toString(), remotePath);
            } finally {
                Files.deleteIfExists(temporary);
            }
        }

        @Override
        public void createDirectory(String path) throws IOException {
            sftp.mkdir(path);
        }

        @Override
        public void delete(String path, boolean directory) throws IOException {
            if (directory) {
                sftp.rmdir(path);
            } else {
                sftp.rm(path);
            }
        }

        @Override
        public void close() throws IOException {
            try {
                sftp.close();
            } finally {
                SshjConnector.closeQuietly(client);
            }
        }
    }

    private static RemoteFile toRemoteFile(
            String directory,
            RemoteResourceInfo file) {
        return new RemoteFile(
                file.getName(),
                join(directory, file.getName()),
                file.getAttributes().getSize(),
                file.isDirectory(),
                Instant.ofEpochSecond(file.getAttributes().getMtime()));
    }

    private static String join(String directory, String name) {
        return "/".equals(directory)
                ? "/" + name
                : directory.replaceAll("/+$", "") + "/" + name;
    }
}
