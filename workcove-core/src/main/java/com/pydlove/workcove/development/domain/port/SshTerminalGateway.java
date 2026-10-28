package com.pydlove.workcove.development.domain.port;

public interface SshTerminalGateway {

    OpenedTerminal open(
            Connection connection,
            HostKey expectedHostKey,
            int columns,
            int rows,
            TerminalListener listener);

    record Connection(
            String host,
            int port,
            String username,
            String password) {
    }

    record HostKey(
            String algorithm,
            String fingerprint,
            String publicKey) {
    }

    record OpenedTerminal(
            Terminal terminal,
            HostKey hostKey) {
    }

    interface Terminal extends AutoCloseable {

        void write(String data);

        void resize(int columns, int rows);

        @Override
        void close();
    }

    interface TerminalListener {

        void onOutput(String data);

        void onClosed(String error);
    }
}
