package com.pydlove.workcove.development.infrastructure;

import com.pydlove.workcove.development.domain.port.SshTerminalGateway;
import com.pydlove.workcove.shared.domain.DomainException;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SshjTerminalGateway implements SshTerminalGateway {

    private static final Logger log =
            LoggerFactory.getLogger(SshjTerminalGateway.class);
    private final SshjConnector connector = new SshjConnector();

    @Override
    public OpenedTerminal open(
            Connection connection,
            HostKey expectedHostKey,
            int columns,
            int rows,
            TerminalListener listener) {
        SshjConnector.ConnectedClient connected =
                connector.connect(connection, expectedHostKey);
        SSHClient client = connected.client();
        Session session = null;
        try {
            session = client.startSession();
            session.allocatePTY("xterm-256color", columns, rows, 0, 0, Map.of());
            Session.Shell shell = session.startShell();
            SshjTerminal terminal = new SshjTerminal(
                    client,
                    session,
                    shell,
                    listener);
            terminal.start();
            return new OpenedTerminal(terminal, connected.hostKey());
        } catch (IOException exception) {
            if (session != null) {
                try {
                    session.close();
                } catch (Exception ignored) {
                }
            }
            SshjConnector.closeQuietly(client);
            throw new DomainException("无法建立 SSH 终端会话");
        }
    }

    private static final class SshjTerminal implements Terminal {

        private final SSHClient client;
        private final Session session;
        private final Session.Shell shell;
        private final OutputStream input;
        private final TerminalListener listener;
        private final AtomicBoolean closed = new AtomicBoolean();

        private SshjTerminal(
                SSHClient client,
                Session session,
                Session.Shell shell,
                TerminalListener listener) {
            this.client = client;
            this.session = session;
            this.shell = shell;
            this.input = shell.getOutputStream();
            this.listener = listener;
        }

        private void start() {
            startReader(shell.getInputStream());
            startReader(shell.getErrorStream());
        }

        private void startReader(InputStream stream) {
            Thread thread = new Thread(() -> {
                char[] buffer = new char[4096];
                try (InputStreamReader reader = new InputStreamReader(
                        stream,
                        StandardCharsets.UTF_8)) {
                    int read;
                    while ((read = reader.read(buffer)) >= 0) {
                        if (read > 0) {
                            listener.onOutput(new String(buffer, 0, read));
                        }
                    }
                    closeInternal(true, "SSH 连接已断开");
                } catch (IOException exception) {
                    closeInternal(true, "SSH 连接已中断");
                }
            }, "workcove-ssh-reader");
            thread.setDaemon(true);
            thread.start();
        }

        @Override
        public void write(String data) {
            if (closed.get()) return;
            try {
                input.write(data.getBytes(StandardCharsets.UTF_8));
                input.flush();
            } catch (IOException exception) {
                closeInternal(true, "SSH 输入通道已关闭");
            }
        }

        @Override
        public void resize(int columns, int rows) {
            if (closed.get()) return;
            try {
                shell.changeWindowDimensions(columns, rows, 0, 0);
            } catch (Exception exception) {
                log.debug("Unable to resize SSH terminal", exception);
            }
        }

        @Override
        public void close() {
            closeInternal(false, null);
        }

        private void closeInternal(boolean notify, String error) {
            if (!closed.compareAndSet(false, true)) return;
            try {
                shell.close();
            } catch (Exception ignored) {
            }
            try {
                session.close();
            } catch (Exception ignored) {
            }
            SshjConnector.closeQuietly(client);
            if (notify) listener.onClosed(error);
        }
    }
}
