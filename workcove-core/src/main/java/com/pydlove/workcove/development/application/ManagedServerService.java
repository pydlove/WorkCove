package com.pydlove.workcove.development.application;

import com.pydlove.workcove.development.application.command.ManagedServerCommand;
import com.pydlove.workcove.development.application.query.ManagedServerView;
import com.pydlove.workcove.development.domain.model.ManagedServer;
import com.pydlove.workcove.development.domain.model.ServerTool;
import com.pydlove.workcove.development.domain.port.CredentialCipher;
import com.pydlove.workcove.development.domain.port.ServerLauncher;
import com.pydlove.workcove.development.domain.repository.ManagedServerRepository;
import com.pydlove.workcove.shared.domain.DomainException;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

public class ManagedServerService {

    private final ManagedServerRepository repository;
    private final CredentialCipher credentialCipher;
    private final ServerLauncher serverLauncher;
    private final Clock clock;

    public ManagedServerService(
            ManagedServerRepository repository,
            CredentialCipher credentialCipher,
            ServerLauncher serverLauncher,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "服务器仓储");
        this.credentialCipher = Objects.requireNonNull(credentialCipher, "凭据加密器");
        this.serverLauncher = Objects.requireNonNull(serverLauncher, "服务器启动器");
        this.clock = Objects.requireNonNull(clock, "时钟");
    }

    public List<ManagedServerView> list(long ownerId, String workspaceId) {
        return repository.findByOwnerIdAndWorkspaceId(
                        ownerId,
                        workspaceId)
                .stream()
                .map(ManagedServerView::from)
                .toList();
    }

    public ManagedServerView create(
            long ownerId,
            String workspaceId,
            ManagedServerCommand command) {
        requirePassword(command.password());
        ManagedServer server = ManagedServer.create(
                ownerId,
                workspaceId,
                command.name(),
                command.host(),
                command.port(),
                command.username(),
                credentialCipher.encrypt(command.password()),
                clock.instant());
        return ManagedServerView.from(repository.save(server));
    }

    public ManagedServerView update(
            long ownerId,
            String workspaceId,
            long id,
            ManagedServerCommand command) {
        ManagedServer existing = requireServer(
                ownerId,
                workspaceId,
                id);
        String passwordCiphertext = command.password() == null
                || command.password().isBlank()
                ? existing.getPasswordCiphertext()
                : credentialCipher.encrypt(command.password());
        ManagedServer updated = existing.update(
                command.name(),
                command.host(),
                command.port(),
                command.username(),
                passwordCiphertext,
                clock.instant());
        return ManagedServerView.from(repository.save(updated));
    }

    public void delete(long ownerId, String workspaceId, long id) {
        requireServer(ownerId, workspaceId, id);
        repository.deleteByIdAndOwnerIdAndWorkspaceId(
                id,
                ownerId,
                workspaceId);
    }

    public ManagedServerView copyToWorkspace(
            long ownerId,
            String sourceWorkspaceId,
            long id,
            String targetWorkspaceId) {
        ManagedServer existing = requireServer(
                ownerId,
                sourceWorkspaceId,
                id);
        if (!existing.getWorkspaceId().equals(sourceWorkspaceId)
                || existing.getWorkspaceId().equals(targetWorkspaceId)) {
            throw new DomainException("服务器不存在或无权访问");
        }
        return ManagedServerView.from(repository.save(
                existing.copyToWorkspace(
                        targetWorkspaceId,
                        existing.getName() + " 副本",
                        clock.instant())));
    }

    public ManagedServerView moveToWorkspace(
            long ownerId,
            String sourceWorkspaceId,
            long id,
            String targetWorkspaceId) {
        ManagedServer existing = requireServer(
                ownerId,
                sourceWorkspaceId,
                id);
        if (!existing.getWorkspaceId().equals(sourceWorkspaceId)
                || existing.getWorkspaceId().equals(targetWorkspaceId)) {
            throw new DomainException("服务器不存在或无权访问");
        }
        return ManagedServerView.from(repository.save(
                existing.moveToWorkspace(
                        targetWorkspaceId,
                        clock.instant())));
    }

    public ServerLauncher.LaunchResult open(
            long ownerId,
            String workspaceId,
            long id,
            ServerTool tool) {
        ManagedServer server = requireServer(ownerId, workspaceId, id);
        String password = credentialCipher.decrypt(server.getPasswordCiphertext());
        return serverLauncher.open(
                new ServerLauncher.ServerConnection(
                        server.getHost(),
                        server.getPort(),
                        server.getUsername(),
                        password),
                tool);
    }

    private ManagedServer requireServer(
            long ownerId,
            String workspaceId,
            long id) {
        return repository.findByIdAndOwnerIdAndWorkspaceId(
                        id,
                        ownerId,
                        workspaceId)
                .orElseThrow(() -> new DomainException("服务器不存在或无权访问"));
    }

    private static void requirePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new DomainException("密码不能为空");
        }
        if (password.length() > 1024) {
            throw new DomainException("密码过长");
        }
    }
}
