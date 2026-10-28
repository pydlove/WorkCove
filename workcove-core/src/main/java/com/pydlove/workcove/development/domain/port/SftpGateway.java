package com.pydlove.workcove.development.domain.port;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;

public interface SftpGateway {

    OpenedSftp open(
            SshTerminalGateway.Connection connection,
            SshTerminalGateway.HostKey expectedHostKey) throws IOException;

    record RemoteFile(
            String name,
            String path,
            long size,
            boolean directory,
            Instant modifiedAt) {
    }

    interface DownloadedFile extends AutoCloseable {

        String name();

        long size();

        InputStream stream();
    }

    interface SftpSession extends AutoCloseable {

        List<RemoteFile> list(String path) throws IOException;

        long size(String path) throws IOException;

        DownloadedFile download(String path) throws IOException;

        void upload(String directory, String filename, InputStream input, long size)
                throws IOException;

        void createDirectory(String path) throws IOException;

        void delete(String path, boolean directory) throws IOException;

        @Override
        void close() throws IOException;
    }

    record OpenedSftp(
            SftpSession session,
            SshTerminalGateway.HostKey hostKey) implements AutoCloseable {

        @Override
        public void close() throws IOException {
            session.close();
        }
    }
}
