package com.pydlove.workcove.development.interfaces.websocket;

import com.pydlove.workcove.identity.application.command.AuthService;
import com.pydlove.workcove.identity.interfaces.security.SessionCookieReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Arrays;
import java.util.Map;

@Configuration
@EnableWebSocket
public class SshWebSocketConfiguration implements WebSocketConfigurer {

    private final SshTerminalWebSocketHandler terminalHandler;
    private final AuthService authService;
    private final SessionCookieReader sessionCookieReader;
    private final String allowedOrigins;

    public SshWebSocketConfiguration(
            SshTerminalWebSocketHandler terminalHandler,
            AuthService authService,
            SessionCookieReader sessionCookieReader,
            @Value("${workcove.web.allowed-origins:http://127.0.0.1:32581,http://localhost:32581}")
            String allowedOrigins) {
        this.terminalHandler = terminalHandler;
        this.authService = authService;
        this.sessionCookieReader = sessionCookieReader;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(
                        terminalHandler,
                        "/ws/development/servers/*/ssh")
                .addInterceptors(new AuthenticationInterceptor())
                .setAllowedOrigins(allowedOrigins.split(","));
    }

    private final class AuthenticationInterceptor implements HandshakeInterceptor {

        @Override
        public boolean beforeHandshake(
                ServerHttpRequest request,
                ServerHttpResponse response,
                WebSocketHandler wsHandler,
                Map<String, Object> attributes) {
            String origin = request.getHeaders().getOrigin();
            if (origin == null
                    || Arrays.stream(allowedOrigins.split(","))
                    .map(String::trim)
                    .noneMatch(origin::equals)) {
                return false;
            }

            ServletServerHttpRequest servletRequest =
                    (ServletServerHttpRequest) request;
            String workspaceId = servletRequest.getServletRequest()
                    .getParameter("workspaceId");
            if (workspaceId == null
                    || workspaceId.isBlank()
                    || workspaceId.length() > 64) {
                return false;
            }
            long userId = authService.currentSession(
                    sessionCookieReader.readToken(
                            servletRequest.getServletRequest()))
                    .user()
                    .id();
            long serverId = pathServerId(request.getURI());
            int columns = queryInt(request.getURI(), "cols", 120, 20, 500);
            int rows = queryInt(request.getURI(), "rows", 32, 5, 200);
            attributes.put("userId", userId);
            attributes.put("workspaceId", workspaceId.trim());
            attributes.put("serverId", serverId);
            attributes.put("columns", columns);
            attributes.put("rows", rows);
            return true;
        }

        @Override
        public void afterHandshake(
                ServerHttpRequest request,
                ServerHttpResponse response,
                WebSocketHandler wsHandler,
                Exception exception) {
        }

    }

    private static long pathServerId(URI uri) {
        String path = uri.getPath();
        String[] segments = path.split("/");
        for (int index = 0; index < segments.length - 1; index++) {
            if ("servers".equals(segments[index])) {
                return Long.parseLong(segments[index + 1]);
            }
        }
        throw new IllegalArgumentException("服务器 ID 无效");
    }

    private static int queryInt(
            URI uri,
            String name,
            int defaultValue,
            int minimum,
            int maximum) {
        String query = uri.getQuery();
        if (query == null || query.isBlank()) return defaultValue;
        for (String item : query.split("&")) {
            String[] parts = item.split("=", 2);
            if (!name.equals(parts[0]) || parts.length < 2) continue;
            int value = Integer.parseInt(parts[1]);
            return Math.max(minimum, Math.min(maximum, value));
        }
        return defaultValue;
    }
}
