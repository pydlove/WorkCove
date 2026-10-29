package com.pydlove.workcove.development.application;

import com.pydlove.workcove.development.domain.model.TestEnvironment;
import com.pydlove.workcove.development.domain.port.CredentialCipher;
import com.pydlove.workcove.development.domain.repository.TestEnvironmentRepository;
import com.pydlove.workcove.shared.domain.DomainException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class TestEnvironmentService {

    private final TestEnvironmentRepository repository;
    private final CredentialCipher credentialCipher;
    private final Clock clock;

    public TestEnvironmentService(
            TestEnvironmentRepository repository,
            CredentialCipher credentialCipher,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.credentialCipher = Objects.requireNonNull(credentialCipher);
        this.clock = Objects.requireNonNull(clock);
    }

    public List<TestEnvironmentView> list(
            long ownerId,
            String workspaceId) {
        String normalizedWorkspaceId =
                requireWorkspaceId(workspaceId);
        return repository.findByOwnerAndWorkspace(
                        ownerId,
                        normalizedWorkspaceId)
                .stream()
                .map(TestEnvironmentView::from)
                .toList();
    }

    public TestEnvironmentView save(
            long ownerId,
            String workspaceId,
            Long id,
            TestEnvironmentCommand command) {
        Objects.requireNonNull(command, "测试环境");
        String normalizedWorkspaceId =
                requireWorkspaceId(workspaceId);
        TestEnvironment existing = id == null
                ? null
                : require(ownerId, normalizedWorkspaceId, id);
        String password = command.password() == null
                ? ""
                : command.password();
        if (existing == null && password.isBlank()) {
            throw new DomainException("密码不能为空");
        }
        String passwordCiphertext = existing == null
                ? credentialCipher.encrypt(password)
                : password.isBlank()
                        ? existing.getPasswordCiphertext()
                        : credentialCipher.encrypt(password);
        Instant now = clock.instant();
        TestEnvironment environment = existing == null
                ? TestEnvironment.create(
                        ownerId,
                        normalizedWorkspaceId,
                        command.name(),
                        command.url(),
                        command.username(),
                        passwordCiphertext,
                        now)
                : existing.update(
                        command.name(),
                        command.url(),
                        command.username(),
                        passwordCiphertext,
                        now);
        return TestEnvironmentView.from(repository.save(environment));
    }

    public void delete(
            long ownerId,
            String workspaceId,
            long id) {
        String normalizedWorkspaceId = requireWorkspaceId(workspaceId);
        require(ownerId, normalizedWorkspaceId, id);
        repository.deleteByIdAndOwnerAndWorkspace(
                id,
                ownerId,
                normalizedWorkspaceId);
    }

    public TestEnvironmentCredentials credentials(
            long ownerId,
            String workspaceId,
            long id) {
        TestEnvironment environment =
                require(ownerId, workspaceId, id);
        return new TestEnvironmentCredentials(
                environment.getUsername(),
                credentialCipher.decrypt(
                        environment.getPasswordCiphertext()));
    }

    private TestEnvironment require(
            long ownerId,
            String workspaceId,
            long id) {
        return repository.findByIdAndOwnerAndWorkspace(
                        id,
                        ownerId,
                        requireWorkspaceId(workspaceId))
                .orElseThrow(() -> new DomainException(
                        "测试环境不存在或无权访问"));
    }

    private static String requireWorkspaceId(String workspaceId) {
        String normalized = workspaceId == null
                ? ""
                : workspaceId.trim();
        if (normalized.isEmpty()) {
            throw new DomainException("工作空间不能为空");
        }
        return normalized;
    }

    public record TestEnvironmentCommand(
            String name,
            String url,
            String username,
            String password
    ) {
    }

    public record TestEnvironmentView(
            Long id,
            String workspaceId,
            String name,
            String url,
            String username,
            boolean hasPassword,
            Instant createdAt,
            Instant updatedAt
    ) {
        static TestEnvironmentView from(TestEnvironment environment) {
            return new TestEnvironmentView(
                    environment.getId(),
                    environment.getWorkspaceId(),
                    environment.getName(),
                    environment.getUrl(),
                    environment.getUsername(),
                    !environment.getPasswordCiphertext().isBlank(),
                    environment.getCreatedAt(),
                    environment.getUpdatedAt());
        }
    }

    public record TestEnvironmentCredentials(
            String username,
            String password
    ) {
    }
}
