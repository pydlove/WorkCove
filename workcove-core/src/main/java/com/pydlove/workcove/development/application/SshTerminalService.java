package com.pydlove.workcove.development.application;

import com.pydlove.workcove.development.domain.model.ManagedServer;
import com.pydlove.workcove.development.domain.port.CredentialCipher;
import com.pydlove.workcove.development.domain.port.SshTerminalGateway;
import com.pydlove.workcove.development.domain.repository.ManagedServerRepository;
import com.pydlove.workcove.shared.domain.DomainException;

import java.time.Clock;
import java.util.Objects;

public class SshTerminalService {

    private final ManagedServerRepository repository;
    private final CredentialCipher credentialCipher;
    private final SshTerminalGateway gateway;
    private final Clock clock;

    public SshTerminalService(
            ManagedServerRepository repository,
            CredentialCipher credentialCipher,
            SshTerminalGateway gateway,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "服务器仓储");
        this.credentialCipher = Objects.requireNonNull(credentialCipher, "凭据加密器");
        this.gateway = Objects.requireNonNull(gateway, "SSH 网关");
        this.clock = Objects.requireNonNull(clock, "时钟");
    }

    public TerminalConnection open(
            long ownerId,
            long serverId,
            int columns,
            int rows,
            SshTerminalGateway.TerminalListener listener) {
        if (columns < 20 || columns > 500 || rows < 5 || rows > 200) {
            throw new DomainException("终端尺寸无效");
        }
        ManagedServer server = repository.findByIdAndOwnerId(serverId, ownerId)
                .orElseThrow(() -> new DomainException("服务器不存在或无权访问"));
        SshTerminalGateway.HostKey expected = server.getHostKeyPublicKey() == null
                ? null
                : new SshTerminalGateway.HostKey(
                        server.getHostKeyAlgorithm(),
                        server.getHostKeyFingerprint(),
                        server.getHostKeyPublicKey());
        boolean firstTrust = expected == null;
        SshTerminalGateway.OpenedTerminal opened = gateway.open(
                new SshTerminalGateway.Connection(
                        server.getHost(),
                        server.getPort(),
                        server.getUsername(),
                        credentialCipher.decrypt(server.getPasswordCiphertext())),
                expected,
                columns,
                rows,
                listener);
        try {
            if (firstTrust
                    || !expected.publicKey().equals(
                            opened.hostKey().publicKey())) {
                repository.save(server.rememberHostKey(
                        opened.hostKey().algorithm(),
                        opened.hostKey().fingerprint(),
                        opened.hostKey().publicKey(),
                        clock.instant()));
            }
        } catch (RuntimeException exception) {
            opened.terminal().close();
            throw exception;
        }
        return new TerminalConnection(
                opened.terminal(),
                opened.hostKey().fingerprint(),
                firstTrust);
    }

    public record TerminalConnection(
            SshTerminalGateway.Terminal terminal,
            String hostKeyFingerprint,
            boolean firstTrust) {
    }
}
