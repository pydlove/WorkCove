package com.pydlove.workcove.development.application;

import com.pydlove.workcove.development.application.command.DeploymentExecutionCommand;
import com.pydlove.workcove.development.domain.model.DeploymentExecutionResult;
import com.pydlove.workcove.development.domain.model.DeploymentExecutionStatus;
import com.pydlove.workcove.development.domain.model.ManagedServer;
import com.pydlove.workcove.development.domain.port.CredentialCipher;
import com.pydlove.workcove.development.domain.port.RemoteDeploymentGateway;
import com.pydlove.workcove.development.domain.port.SshTerminalGateway;
import com.pydlove.workcove.development.domain.repository.ManagedServerRepository;
import com.pydlove.workcove.shared.domain.DomainException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public class DeploymentExecutionService {

    private static final int MAX_DIRECTORY_LENGTH = 512;
    private static final int MAX_FILENAME_LENGTH = 255;
    private static final int MAX_SCRIPT_BYTES = 512 * 1024;
    private static final Duration EXECUTION_TIMEOUT = Duration.ofMinutes(10);

    private final ManagedServerRepository serverRepository;
    private final CredentialCipher credentialCipher;
    private final RemoteDeploymentGateway gateway;
    private final Clock clock;

    public DeploymentExecutionService(
            ManagedServerRepository serverRepository,
            CredentialCipher credentialCipher,
            RemoteDeploymentGateway gateway,
            Clock clock) {
        this.serverRepository = Objects.requireNonNull(
                serverRepository,
                "服务器仓储");
        this.credentialCipher = Objects.requireNonNull(
                credentialCipher,
                "凭据加密器");
        this.gateway = Objects.requireNonNull(
                gateway,
                "远程部署网关");
        this.clock = Objects.requireNonNull(clock, "时钟");
    }

    public DeploymentExecutionResult execute(
            long ownerId,
            DeploymentExecutionCommand command) {
        String workspaceId = required(
                command.workspaceId(),
                "工作空间",
                64);
        if (command.serverId() == null) {
            throw new DomainException("请选择部署服务器");
        }
        String remoteDirectory = requireRemoteDirectory(
                command.remoteDirectory());
        String scriptFilename = requireScriptFilename(
                command.scriptFilename());
        String scriptContent = requireScript(command.scriptContent());

        ManagedServer server = serverRepository
                .findByIdAndOwnerIdAndWorkspaceId(
                        command.serverId(),
                        ownerId,
                        workspaceId)
                .orElseThrow(() -> new DomainException(
                        "服务器不存在或无权访问"));

        SshTerminalGateway.HostKey expectedHostKey =
                server.getHostKeyPublicKey() == null
                        ? null
                        : new SshTerminalGateway.HostKey(
                                server.getHostKeyAlgorithm(),
                                server.getHostKeyFingerprint(),
                                server.getHostKeyPublicKey());
        Instant startedAt = clock.instant();
        RemoteDeploymentGateway.Result result = gateway.execute(
                new SshTerminalGateway.Connection(
                        server.getHost(),
                        server.getPort(),
                        server.getUsername(),
                        credentialCipher.decrypt(
                                server.getPasswordCiphertext())),
                expectedHostKey,
                remoteDirectory,
                scriptFilename,
                scriptContent,
                EXECUTION_TIMEOUT);
        Instant finishedAt = clock.instant();

        if (result.hostKey() != null
                && (expectedHostKey == null
                || !expectedHostKey.publicKey().equals(
                        result.hostKey().publicKey()))) {
            serverRepository.save(server.rememberHostKey(
                    result.hostKey().algorithm(),
                    result.hostKey().fingerprint(),
                    result.hostKey().publicKey(),
                    finishedAt));
        }

        if (result.timedOut()) {
            return new DeploymentExecutionResult(
                    DeploymentExecutionStatus.TIMEOUT,
                    null,
                    "部署超时（10 分钟）",
                    outputOrEmpty(result.output()),
                    startedAt,
                    finishedAt);
        }
        if (result.exitCode() != null && result.exitCode() == 0) {
            return new DeploymentExecutionResult(
                    DeploymentExecutionStatus.SUCCESS,
                    0,
                    "部署完成（退出码 0）",
                    outputOrEmpty(result.output()),
                    startedAt,
                    finishedAt);
        }

        String summary = result.exitCode() == null
                ? "部署脚本执行失败"
                : "部署失败（退出码 " + result.exitCode() + "）";
        return new DeploymentExecutionResult(
                DeploymentExecutionStatus.FAILED,
                result.exitCode(),
                summary,
                outputOrEmpty(result.output()),
                startedAt,
                finishedAt);
    }

    private static String requireRemoteDirectory(String value) {
        String directory = required(value, "远程目录", MAX_DIRECTORY_LENGTH);
        if (!directory.startsWith("/")) {
            throw new DomainException("远程目录必须是以 / 开头的绝对路径");
        }
        if (directory.indexOf('\n') >= 0 || directory.indexOf('\r') >= 0) {
            throw new DomainException("远程目录包含无效字符");
        }
        return directory;
    }

    private static String requireScriptFilename(String value) {
        String filename = required(
                value,
                "脚本文件名",
                MAX_FILENAME_LENGTH);
        if (filename.contains("/") || filename.contains("\\")) {
            throw new DomainException("脚本文件名不能包含路径");
        }
        return filename;
    }

    private static String requireScript(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainException("请上传或填写部署脚本");
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > MAX_SCRIPT_BYTES) {
            throw new DomainException("部署脚本不能超过 512 KB");
        }
        if (value.indexOf('\0') >= 0) {
            throw new DomainException("部署脚本包含无效字符");
        }
        return value;
    }

    private static String required(
            String value,
            String label,
            int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new DomainException("请输入" + label);
        }
        if (normalized.codePointCount(0, normalized.length()) > maxLength) {
            throw new DomainException(
                    label + "不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private static String outputOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
