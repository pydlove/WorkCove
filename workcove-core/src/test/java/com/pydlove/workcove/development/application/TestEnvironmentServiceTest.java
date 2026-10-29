package com.pydlove.workcove.development.application;

import com.pydlove.workcove.development.domain.model.TestEnvironment;
import com.pydlove.workcove.development.domain.port.CredentialCipher;
import com.pydlove.workcove.development.domain.repository.TestEnvironmentRepository;
import com.pydlove.workcove.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestEnvironmentServiceTest {

    private static final long OWNER_ID = 42L;
    private static final String WORKSPACE_ID = "workspace-a";
    private static final Instant NOW =
            Instant.parse("2026-10-29T02:00:00Z");

    private final InMemoryRepository repository =
            new InMemoryRepository();
    private final PrefixCipher cipher = new PrefixCipher();
    private final TestEnvironmentService service =
            new TestEnvironmentService(
                    repository,
                    cipher,
                    Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void savesEncryptedCredentialsWithoutExposingPassword() {
        var created = service.save(
                OWNER_ID,
                WORKSPACE_ID,
                null,
                command("研发环境", "https://dev.example.com", "alice", "secret"));

        assertEquals("enc:secret", repository.environment.getPasswordCiphertext());
        assertTrue(created.hasPassword());

        var credentials = service.credentials(
                OWNER_ID,
                WORKSPACE_ID,
                created.id());
        assertEquals("alice", credentials.username());
        assertEquals("secret", credentials.password());
    }

    @Test
    void requiresPasswordWhenCreatingEnvironment() {
        assertThrows(
                DomainException.class,
                () -> service.save(
                        OWNER_ID,
                        WORKSPACE_ID,
                        null,
                        command(
                                "研发环境",
                                "https://dev.example.com",
                                "alice",
                                " ")));
        assertEquals(null, repository.environment);
    }

    @Test
    void keepsExistingPasswordWhenEditingWithBlankPassword() {
        var created = service.save(
                OWNER_ID,
                WORKSPACE_ID,
                null,
                command("研发环境", "https://dev.example.com", "alice", "secret"));

        service.save(
                OWNER_ID,
                WORKSPACE_ID,
                created.id(),
                command("研发环境", "https://dev.example.com", "bob", ""));

        assertEquals("enc:secret", repository.environment.getPasswordCiphertext());
        assertEquals("bob", repository.environment.getUsername());
    }

    @Test
    void isolatesEnvironmentsByOwnerAndWorkspace() {
        var created = service.save(
                OWNER_ID,
                WORKSPACE_ID,
                null,
                command("研发环境", "https://dev.example.com", "alice", "secret"));

        assertFalse(service.list(99L, WORKSPACE_ID).contains(created));
        assertThrows(
                DomainException.class,
                () -> service.credentials(
                        OWNER_ID,
                        "workspace-b",
                        created.id()));
        assertThrows(
                DomainException.class,
                () -> service.credentials(
                        99L,
                        WORKSPACE_ID,
                        created.id()));
        assertThrows(
                DomainException.class,
                () -> service.delete(
                        OWNER_ID,
                        "workspace-b",
                        created.id()));
        assertEquals(
                1,
                service.list(OWNER_ID, WORKSPACE_ID).size());
    }

    @Test
    void rejectsInvalidHttpUrl() {
        assertThrows(
                DomainException.class,
                () -> service.save(
                        OWNER_ID,
                        WORKSPACE_ID,
                        null,
                        command(
                                "研发环境",
                                "ftp://dev.example.com",
                                "alice",
                                "secret")));
    }

    private static TestEnvironmentService.TestEnvironmentCommand command(
            String name,
            String url,
            String username,
            String password) {
        return new TestEnvironmentService.TestEnvironmentCommand(
                name,
                url,
                username,
                password);
    }

    private static final class PrefixCipher implements CredentialCipher {
        @Override
        public String encrypt(String rawValue) {
            return "enc:" + rawValue;
        }

        @Override
        public String decrypt(String encryptedValue) {
            return encryptedValue.substring("enc:".length());
        }
    }

    private static final class InMemoryRepository
            implements TestEnvironmentRepository {

        private TestEnvironment environment;
        private long nextId = 1L;

        @Override
        public List<TestEnvironment> findByOwnerAndWorkspace(
                long ownerId,
                String workspaceId) {
            if (environment == null
                    || environment.getOwnerId() != ownerId
                    || !environment.getWorkspaceId().equals(workspaceId)) {
                return List.of();
            }
            return new ArrayList<>(List.of(environment));
        }

        @Override
        public Optional<TestEnvironment> findByIdAndOwnerAndWorkspace(
                long id,
                long ownerId,
                String workspaceId) {
            return environment != null
                    && environment.getId() == id
                    && environment.getOwnerId() == ownerId
                    && environment.getWorkspaceId().equals(workspaceId)
                    ? Optional.of(environment)
                    : Optional.empty();
        }

        @Override
        public TestEnvironment save(TestEnvironment next) {
            environment = TestEnvironment.restore(
                    next.getId() == null ? nextId++ : next.getId(),
                    next.getOwnerId(),
                    next.getWorkspaceId(),
                    next.getName(),
                    next.getUrl(),
                    next.getUsername(),
                    next.getPasswordCiphertext(),
                    next.getCreatedAt(),
                    next.getUpdatedAt());
            return environment;
        }

        @Override
        public void deleteByIdAndOwnerAndWorkspace(
                long id,
                long ownerId,
                String workspaceId) {
            if (environment != null
                    && environment.getId() == id
                    && environment.getOwnerId() == ownerId
                    && environment.getWorkspaceId().equals(workspaceId)) {
                environment = null;
            }
        }
    }
}
