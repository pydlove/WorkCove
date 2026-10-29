package com.pydlove.workcove.development.interfaces.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pydlove.workcove.development.application.SshTerminalService;
import com.pydlove.workcove.development.domain.port.SshTerminalGateway;
import com.pydlove.workcove.shared.domain.DomainException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;

@Component
public class SshTerminalWebSocketHandler extends TextWebSocketHandler {

    private static final String TERMINAL_ATTRIBUTE = "workcove.ssh.terminal";

    private final SshTerminalService terminalService;
    private final ObjectMapper objectMapper;

    public SshTerminalWebSocketHandler(
            SshTerminalService terminalService,
            ObjectMapper objectMapper) {
        this.terminalService = terminalService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session)
            throws Exception {
        Map<String, Object> attributes = session.getAttributes();
        long userId = ((Number) attributes.get("userId")).longValue();
        String workspaceId = String.valueOf(attributes.get("workspaceId"));
        long serverId = ((Number) attributes.get("serverId")).longValue();
        int columns = ((Number) attributes.get("columns")).intValue();
        int rows = ((Number) attributes.get("rows")).intValue();

        try {
            SshTerminalService.TerminalConnection connection = terminalService.open(
                    userId,
                    workspaceId,
                    serverId,
                    columns,
                    rows,
                    new WebSocketTerminalListener(session));
            session.getAttributes().put(TERMINAL_ATTRIBUTE, connection.terminal());
            send(session, new Event(
                    "connected",
                    null,
                    connection.hostKeyFingerprint(),
                    connection.firstTrust(),
                    null));
        } catch (Exception exception) {
            String message = exception instanceof DomainException
                    ? exception.getMessage()
                    : "SSH 连接失败";
            send(session, new Event("error", null, null, false, message));
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    protected void handleTextMessage(
            WebSocketSession session,
            TextMessage message) throws Exception {
        if (message.getPayloadLength() > 1_048_576) {
            session.close(CloseStatus.TOO_BIG_TO_PROCESS);
            return;
        }
        SshTerminalGateway.Terminal terminal = terminal(session);
        if (terminal == null) return;

        TerminalMessage command = objectMapper.readValue(
                message.getPayload(),
                TerminalMessage.class);
        if ("input".equals(command.type()) && command.data() != null) {
            terminal.write(command.data());
        } else if ("resize".equals(command.type())) {
            terminal.resize(command.columns(), command.rows());
        } else if ("close".equals(command.type())) {
            session.close(CloseStatus.NORMAL);
        }
    }

    @Override
    public void handleTransportError(
            WebSocketSession session,
            Throwable exception) throws Exception {
        closeTerminal(session);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    public void afterConnectionClosed(
            WebSocketSession session,
            CloseStatus status) {
        closeTerminal(session);
    }

    private SshTerminalGateway.Terminal terminal(WebSocketSession session) {
        Object value = session.getAttributes().get(TERMINAL_ATTRIBUTE);
        return value instanceof SshTerminalGateway.Terminal terminal
                ? terminal
                : null;
    }

    private void closeTerminal(WebSocketSession session) {
        SshTerminalGateway.Terminal terminal = terminal(session);
        if (terminal != null) {
            terminal.close();
            session.getAttributes().remove(TERMINAL_ATTRIBUTE);
        }
    }

    private void send(WebSocketSession session, Event event) {
        if (!session.isOpen()) return;
        try {
            String payload = objectMapper.writeValueAsString(event);
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(payload));
                }
            }
        } catch (IOException ignored) {
        }
    }

    private final class WebSocketTerminalListener
            implements SshTerminalGateway.TerminalListener {

        private final WebSocketSession session;

        private WebSocketTerminalListener(WebSocketSession session) {
            this.session = session;
        }

        @Override
        public void onOutput(String data) {
            send(session, new Event("output", data, null, false, null));
        }

        @Override
        public void onClosed(String error) {
            send(session, new Event("closed", null, null, false, error));
            try {
                if (session.isOpen()) session.close(CloseStatus.NORMAL);
            } catch (IOException ignored) {
            }
        }
    }

    private record TerminalMessage(
            String type,
            String data,
            int columns,
            int rows) {
    }

    private record Event(
            String type,
            String data,
            String fingerprint,
            boolean firstTrust,
            String message) {
    }
}
