package com.pydlove.workcove.vault.application;

import com.pydlove.workcove.development.domain.port.CredentialCipher;
import com.pydlove.workcove.identity.domain.model.Email;
import com.pydlove.workcove.identity.domain.model.PasswordHash;
import com.pydlove.workcove.identity.domain.model.SessionTokenHash;
import com.pydlove.workcove.identity.domain.model.User;
import com.pydlove.workcove.identity.domain.model.UserId;
import com.pydlove.workcove.identity.domain.port.PasswordHasher;
import com.pydlove.workcove.identity.domain.port.SessionTokenCodec;
import com.pydlove.workcove.identity.domain.repository.UserRepository;
import com.pydlove.workcove.shared.domain.AuthenticationException;
import com.pydlove.workcove.shared.domain.DomainException;
import com.pydlove.workcove.vault.domain.model.VaultChallenge;
import com.pydlove.workcove.vault.domain.model.VaultCategory;
import com.pydlove.workcove.vault.domain.model.VaultEntry;
import com.pydlove.workcove.vault.domain.model.VaultPasskey;
import com.pydlove.workcove.vault.domain.model.VaultUnlockSession;
import com.pydlove.workcove.vault.domain.port.VaultWebAuthnGateway;
import com.pydlove.workcove.vault.domain.repository.VaultChallengeRepository;
import com.pydlove.workcove.vault.domain.repository.VaultCategoryRepository;
import com.pydlove.workcove.vault.domain.repository.VaultEntryRepository;
import com.pydlove.workcove.vault.domain.repository.VaultPasskeyRepository;
import com.pydlove.workcove.vault.domain.repository.VaultUnlockSessionRepository;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VaultServiceTest {

    private static final long OWNER_ID = 10L;
    private static final long OTHER_OWNER_ID = 20L;
    private static final String WORKSPACE_ID = "default";
    private static final Instant NOW = Instant.parse("2026-10-27T00:00:00Z");

    private final MutableClock clock = new MutableClock(NOW);
    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final InMemoryEntryRepository entries = new InMemoryEntryRepository();
    private final InMemoryCategoryRepository categories =
            new InMemoryCategoryRepository(entries);
    private final InMemoryPasskeyRepository passkeys =
            new InMemoryPasskeyRepository();
    private final InMemoryChallengeRepository challenges =
            new InMemoryChallengeRepository();
    private final InMemoryUnlockRepository unlocks =
            new InMemoryUnlockRepository();
    private final FakeCipher cipher = new FakeCipher();
    private final FakeTokenCodec tokenCodec = new FakeTokenCodec();
    private final FakeWebAuthnGateway webAuthn = new FakeWebAuthnGateway();
    private final VaultService service = new VaultService(
            entries,
            categories,
            passkeys,
            challenges,
            unlocks,
            users,
            new FakePasswordHasher(),
            tokenCodec,
            cipher,
            webAuthn,
            clock,
            Duration.ofHours(1));

    @Test
    void unlocksWithTheLoginPasswordAndExpiresAfterOneHour() {
        VaultService.UnlockGrant grant =
                service.unlockWithPassword(OWNER_ID, "correct-password");

        VaultService.VaultStatus status =
                service.status(OWNER_ID, grant.rawToken());
        assertTrue(status.unlocked());
        assertEquals(NOW.plus(Duration.ofHours(1)), status.expiresAt());

        clock.advance(Duration.ofHours(1).plusSeconds(1));
        assertFalse(service.status(OWNER_ID, grant.rawToken()).unlocked());
        assertThrows(
                AuthenticationException.class,
                () -> service.entries(
                        OWNER_ID,
                        WORKSPACE_ID,
                        grant.rawToken()));
    }

    @Test
    void acceptsOnlySupportedUnlockDurations() {
        VaultService.UnlockGrant grant =
                service.unlockWithPassword(
                        OWNER_ID,
                        "correct-password",
                        120);

        assertEquals(
                NOW.plus(Duration.ofHours(2)),
                grant.expiresAt());
        assertThrows(
                DomainException.class,
                () -> service.unlockWithPassword(
                        OWNER_ID,
                        "correct-password",
                        10));
    }

    @Test
    void rejectsAWrongLoginPassword() {
        assertThrows(
                AuthenticationException.class,
                () -> service.unlockWithPassword(OWNER_ID, "wrong"));
    }

    @Test
    void encryptsSecretsAndEnforcesOwnership() {
        String token = service
                .unlockWithPassword(OWNER_ID, "correct-password")
                .rawToken();
        VaultService.VaultEntryView saved = service.saveEntry(
                OWNER_ID,
                WORKSPACE_ID,
                token,
                null,
                new VaultService.VaultEntryInput(
                        "生产数据库",
                        "root",
                        "secret-password",
                        "https://db.example.com",
                        "数据库",
                        "仅内网访问"));

        assertEquals("enc:secret-password", cipher.encrypted);
        assertEquals(
                "secret-password",
                service.revealPassword(
                        OWNER_ID,
                        WORKSPACE_ID,
                        saved.id(),
                        token));
        assertThrows(
                DomainException.class,
                () -> service.revealPassword(
                        20L,
                        WORKSPACE_ID,
                        saved.id(),
                        token));
    }

    @Test
    void managesCategoriesAndReassignsEntriesWithoutChangingSecrets() {
        String token = service
                .unlockWithPassword(OWNER_ID, "correct-password")
                .rawToken();
        VaultService.VaultCategoryView defaultCategory =
                service.categories(
                        OWNER_ID,
                        WORKSPACE_ID,
                        token).get(0);
        VaultService.VaultEntryView entry = service.saveEntry(
                OWNER_ID,
                WORKSPACE_ID,
                token,
                null,
                new VaultService.VaultEntryInput(
                        "生产数据库",
                        "root",
                        "secret-password",
                        "https://db.example.com",
                        "数据库",
                        "仅内网访问"));
        VaultService.VaultCategoryView databaseCategory =
                service.categories(
                        OWNER_ID,
                        WORKSPACE_ID,
                        token).stream()
                        .filter((category) ->
                                category.name().equals("数据库"))
                        .findFirst()
                        .orElseThrow();

        service.renameCategory(
                OWNER_ID,
                WORKSPACE_ID,
                token,
                databaseCategory.id(),
                "数据服务");
        assertEquals(
                "数据服务",
                service.entries(
                        OWNER_ID,
                        WORKSPACE_ID,
                        token).get(0).category());

        service.deleteCategory(
                OWNER_ID,
                WORKSPACE_ID,
                token,
                databaseCategory.id(),
                defaultCategory.id());
        assertEquals(
                defaultCategory.name(),
                service.entries(
                        OWNER_ID,
                        WORKSPACE_ID,
                        token).get(0).category());
        assertEquals(
                "secret-password",
                service.revealPassword(
                        OWNER_ID,
                        WORKSPACE_ID,
                        entry.id(),
                        token));
        assertThrows(
                AuthenticationException.class,
                () -> service.categories(
                        OWNER_ID,
                        WORKSPACE_ID,
                        "expired-token"));
    }

    @Test
    void isolatesCategoriesBetweenUsers() {
        String ownerToken = service
                .unlockWithPassword(OWNER_ID, "correct-password")
                .rawToken();
        String otherToken = service
                .unlockWithPassword(OTHER_OWNER_ID, "correct-password")
                .rawToken();
        VaultService.VaultCategoryView ownerCategory =
                service.addCategory(
                        OWNER_ID,
                        WORKSPACE_ID,
                        ownerToken,
                        "生产账号");

        assertTrue(service.categories(
                        OTHER_OWNER_ID,
                        WORKSPACE_ID,
                        otherToken).stream()
                .noneMatch((category) ->
                        category.name().equals(ownerCategory.name())));
        assertThrows(
                DomainException.class,
                () -> service.renameCategory(
                        OTHER_OWNER_ID,
                        WORKSPACE_ID,
                        otherToken,
                        ownerCategory.id(),
                        "越权修改"));
    }

    @Test
    void isolatesEntriesByWorkspaceAndSupportsCopyAndMove() {
        String token = service
                .unlockWithPassword(OWNER_ID, "correct-password")
                .rawToken();
        VaultService.VaultEntryView source = service.saveEntry(
                OWNER_ID,
                WORKSPACE_ID,
                token,
                null,
                new VaultService.VaultEntryInput(
                        "生产数据库",
                        "root",
                        "secret-password",
                        "https://db.example.com",
                        "数据库",
                        "仅内网访问"));

        assertTrue(service.entries(
                OWNER_ID,
                "second",
                token).isEmpty());

        VaultService.VaultEntryView copied = service.copyEntry(
                OWNER_ID,
                WORKSPACE_ID,
                token,
                source.id(),
                "second");
        assertEquals("second", copied.workspaceId());
        assertEquals(1, service.entries(
                OWNER_ID,
                WORKSPACE_ID,
                token).size());
        assertEquals(1, service.entries(
                OWNER_ID,
                "second",
                token).size());
        assertEquals(
                "secret-password",
                service.revealPassword(
                        OWNER_ID,
                        "second",
                        copied.id(),
                        token));

        VaultService.VaultEntryView moved = service.moveEntry(
                OWNER_ID,
                WORKSPACE_ID,
                token,
                source.id(),
                "third");
        assertEquals("third", moved.workspaceId());
        assertTrue(service.entries(
                OWNER_ID,
                WORKSPACE_ID,
                token).isEmpty());
        assertEquals(1, service.entries(
                OWNER_ID,
                "third",
                token).size());
    }

    @Test
    void passkeyRegistrationAndAuthenticationIssueAnUnlockSession() {
        String passwordToken = service
                .unlockWithPassword(OWNER_ID, "correct-password")
                .rawToken();
        VaultService.RegistrationOptions registration =
                service.passkeyRegistrationOptions(OWNER_ID, passwordToken);
        assertFalse(registration.challenge().isBlank());

        service.finishPasskeyRegistration(
                OWNER_ID,
                passwordToken,
                "{}",
                "http://localhost:32581",
                "localhost");
        assertTrue(service.status(OWNER_ID, null).passkeyEnabled());

        VaultService.AuthenticationOptions authentication =
                service.passkeyAuthenticationOptions(OWNER_ID);
        assertEquals(List.of("credential-1"), authentication.credentialIds());

        VaultService.UnlockGrant grant =
                service.finishPasskeyAuthentication(
                        OWNER_ID,
                        "credential-1",
                        "{}",
                        "http://localhost:32581",
                        "localhost");
        assertTrue(service.status(OWNER_ID, grant.rawToken()).unlocked());
        assertEquals(3, passkeys.lastSignCount);
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }

    private static final class FakePasswordHasher implements PasswordHasher {
        @Override
        public PasswordHash hash(String rawPassword) {
            return new PasswordHash("hash:" + rawPassword);
        }

        @Override
        public boolean matches(
                String rawPassword,
                PasswordHash encodedPassword) {
            return encodedPassword.value().equals("hash:" + rawPassword);
        }
    }

    private static final class FakeCipher implements CredentialCipher {
        private String encrypted = "";

        @Override
        public String encrypt(String rawValue) {
            encrypted = "enc:" + rawValue;
            return encrypted;
        }

        @Override
        public String decrypt(String encryptedValue) {
            return encryptedValue.substring("enc:".length());
        }
    }

    private static final class FakeTokenCodec implements SessionTokenCodec {
        private int sequence;

        @Override
        public String generateRawToken() {
            sequence += 1;
            return "vault-token-" + sequence;
        }

        @Override
        public SessionTokenHash hash(String rawToken) {
            try {
                byte[] hash = MessageDigest.getInstance("SHA-256")
                        .digest(rawToken.getBytes(StandardCharsets.UTF_8));
                return new SessionTokenHash(HexFormat.of().formatHex(hash));
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }
    }

    private static final class FakeWebAuthnGateway
            implements VaultWebAuthnGateway {

        @Override
        public RegistrationVerification verifyRegistration(
                String responseJson,
                byte[] challenge,
                String origin,
                String rpId) {
            return new RegistrationVerification("credential-1", 2);
        }

        @Override
        public AuthenticationVerification verifyAuthentication(
                String registrationJson,
                String credentialId,
                long storedSignCount,
                String responseJson,
                byte[] challenge,
                String origin,
                String rpId) {
            return new AuthenticationVerification(credentialId, 3);
        }
    }

    private static final class InMemoryUserRepository
            implements UserRepository {

        private final User user = User.restore(
                UserId.of(OWNER_ID),
                Email.of("vault@example.com"),
                "Vault User",
                new PasswordHash("hash:correct-password"),
                NOW);
        private final User otherUser = User.restore(
                UserId.of(OTHER_OWNER_ID),
                Email.of("other-vault@example.com"),
                "Other Vault User",
                new PasswordHash("hash:correct-password"),
                NOW);

        @Override
        public Optional<User> findById(UserId id) {
            if (id.value() == OWNER_ID) return Optional.of(user);
            if (id.value() == OTHER_OWNER_ID) return Optional.of(otherUser);
            return Optional.empty();
        }

        @Override
        public Optional<User> findByEmail(Email email) {
            return Optional.of(user);
        }

        @Override
        public boolean existsByEmail(Email email) {
            return true;
        }

        @Override
        public User save(User user) {
            return user;
        }
    }

    private static final class InMemoryEntryRepository
            implements VaultEntryRepository {

        private final AtomicLong ids = new AtomicLong();
        private final List<VaultEntry> values = new ArrayList<>();

        @Override
        public List<VaultEntry> findEntriesByOwnerIdAndWorkspaceId(
                long ownerId,
                String workspaceId) {
            return values.stream()
                    .filter((entry) -> entry.ownerId() == ownerId
                            && entry.workspaceId().equals(workspaceId))
                    .toList();
        }

        @Override
        public Optional<VaultEntry> findByIdAndOwnerIdAndWorkspaceId(
                long id,
                long ownerId,
                String workspaceId) {
            return values.stream()
                    .filter((entry) -> entry.id() == id
                            && entry.ownerId() == ownerId
                            && entry.workspaceId().equals(workspaceId))
                    .findFirst();
        }

        @Override
        public VaultEntry save(VaultEntry entry) {
            VaultEntry saved = entry.id() == null
                    ? new VaultEntry(
                            ids.incrementAndGet(),
                            entry.ownerId(),
                            entry.workspaceId(),
                            entry.name(),
                            entry.username(),
                            entry.passwordCiphertext(),
                            entry.url(),
                            entry.category(),
                            entry.notes(),
                            entry.createdAt(),
                            entry.updatedAt())
                    : entry;
            values.removeIf((item) ->
                    item.id() != null && item.id().equals(saved.id()));
            values.add(saved);
            return saved;
        }

        @Override
        public void deleteByIdAndOwnerIdAndWorkspaceId(
                long id,
                long ownerId,
                String workspaceId) {
            values.removeIf((entry) -> entry.id() == id
                    && entry.ownerId() == ownerId
                    && entry.workspaceId().equals(workspaceId));
        }

        @Override
        public void moveToWorkspace(
                long id,
                long ownerId,
                String sourceWorkspaceId,
                String targetWorkspaceId,
                String category,
                Instant updatedAt) {
            for (int index = 0; index < values.size(); index += 1) {
                VaultEntry entry = values.get(index);
                if (entry.id() == id
                        && entry.ownerId() == ownerId
                        && entry.workspaceId().equals(sourceWorkspaceId)) {
                    values.set(index, new VaultEntry(
                            entry.id(),
                            entry.ownerId(),
                            targetWorkspaceId,
                            entry.name(),
                            entry.username(),
                            entry.passwordCiphertext(),
                            entry.url(),
                            category,
                            entry.notes(),
                            entry.createdAt(),
                            updatedAt));
                    return;
                }
            }
        }

        private void renameCategory(
                long ownerId,
                String workspaceId,
                String currentName,
                String nextName,
                Instant updatedAt) {
            for (int index = 0; index < values.size(); index += 1) {
                VaultEntry entry = values.get(index);
                if (entry.ownerId() == ownerId
                        && entry.workspaceId().equals(workspaceId)
                        && entry.category().equals(currentName)) {
                    values.set(index, copyWithCategory(entry, nextName, updatedAt));
                }
            }
        }

        private void reassignCategory(
                long ownerId,
                String workspaceId,
                String currentName,
                String fallbackName,
                Instant updatedAt) {
            for (int index = 0; index < values.size(); index += 1) {
                VaultEntry entry = values.get(index);
                if (entry.ownerId() == ownerId
                        && entry.workspaceId().equals(workspaceId)
                        && entry.category().equals(currentName)) {
                    values.set(
                            index,
                            copyWithCategory(entry, fallbackName, updatedAt));
                }
            }
        }

        private VaultEntry copyWithCategory(
                VaultEntry entry,
                String category,
                Instant updatedAt) {
            return new VaultEntry(
                    entry.id(),
                    entry.ownerId(),
                    entry.workspaceId(),
                    entry.name(),
                    entry.username(),
                    entry.passwordCiphertext(),
                    entry.url(),
                    category,
                    entry.notes(),
                    entry.createdAt(),
                    updatedAt);
        }
    }

    private static final class InMemoryCategoryRepository
            implements VaultCategoryRepository {

        private final AtomicLong ids = new AtomicLong();
        private final List<VaultCategory> values = new ArrayList<>();
        private final InMemoryEntryRepository entries;

        private InMemoryCategoryRepository(InMemoryEntryRepository entries) {
            this.entries = entries;
        }

        @Override
        public List<VaultCategory> findCategoriesByOwnerIdAndWorkspaceId(
                long ownerId,
                String workspaceId) {
            return values.stream()
                    .filter((category) -> category.ownerId() == ownerId
                            && category.workspaceId().equals(workspaceId))
                    .toList();
        }

        @Override
        public Optional<VaultCategory> findCategoryByIdAndOwnerIdAndWorkspaceId(
                long id,
                long ownerId,
                String workspaceId) {
            return values.stream()
                    .filter((category) ->
                            category.id() == id
                                    && category.ownerId() == ownerId
                                    && category.workspaceId().equals(workspaceId))
                    .findFirst();
        }

        @Override
        public Optional<VaultCategory> findCategoryByNameAndOwnerIdAndWorkspaceId(
                String name,
                long ownerId,
                String workspaceId) {
            return values.stream()
                    .filter((category) ->
                            category.ownerId() == ownerId
                                    && category.workspaceId().equals(workspaceId)
                                    && category.name().equals(name))
                    .findFirst();
        }

        @Override
        public VaultCategory saveCategory(VaultCategory category) {
            VaultCategory saved = new VaultCategory(
                    ids.incrementAndGet(),
                    category.ownerId(),
                    category.workspaceId(),
                    category.name(),
                    category.sortOrder(),
                    category.createdAt(),
                    category.updatedAt());
            values.add(saved);
            return saved;
        }

        @Override
        public void renameCategory(
                long ownerId,
                String workspaceId,
                long id,
                String currentName,
                String nextName,
                Instant updatedAt) {
            for (int index = 0; index < values.size(); index += 1) {
                VaultCategory category = values.get(index);
                if (category.ownerId() == ownerId
                        && category.workspaceId().equals(workspaceId)
                        && category.id() == id) {
                    values.set(index, new VaultCategory(
                            category.id(),
                            category.ownerId(),
                            category.workspaceId(),
                            nextName,
                            category.sortOrder(),
                            category.createdAt(),
                            updatedAt));
                }
            }
            entries.renameCategory(
                    ownerId,
                    workspaceId,
                    currentName,
                    nextName,
                    updatedAt);
        }

        @Override
        public void deleteCategoryAndReassignEntries(
                long ownerId,
                String workspaceId,
                long id,
                String categoryName,
                String fallbackName,
                Instant updatedAt) {
            values.removeIf((category) ->
                    category.ownerId() == ownerId
                            && category.workspaceId().equals(workspaceId)
                            && category.id() == id);
            entries.reassignCategory(
                    ownerId,
                    workspaceId,
                    categoryName,
                    fallbackName,
                    updatedAt);
        }
    }

    private static final class InMemoryPasskeyRepository
            implements VaultPasskeyRepository {

        private final AtomicLong ids = new AtomicLong();
        private final List<VaultPasskey> values = new ArrayList<>();
        private long lastSignCount;

        @Override
        public List<VaultPasskey> findPasskeysByOwnerId(long ownerId) {
            return values.stream()
                    .filter((passkey) -> passkey.ownerId() == ownerId)
                    .toList();
        }

        @Override
        public Optional<VaultPasskey> findByCredentialIdAndOwnerId(
                String credentialId,
                long ownerId) {
            return values.stream()
                    .filter((passkey) ->
                            passkey.ownerId() == ownerId
                                    && passkey.credentialId().equals(credentialId))
                    .findFirst();
        }

        @Override
        public VaultPasskey save(VaultPasskey passkey) {
            VaultPasskey saved = new VaultPasskey(
                    ids.incrementAndGet(),
                    passkey.ownerId(),
                    passkey.credentialId(),
                    passkey.registrationResponse(),
                    passkey.signCount(),
                    passkey.createdAt(),
                    passkey.lastUsedAt());
            values.add(saved);
            return saved;
        }

        @Override
        public void updateUsage(
                String credentialId,
                long ownerId,
                long signCount,
                Instant usedAt) {
            lastSignCount = signCount;
            for (int index = 0; index < values.size(); index += 1) {
                VaultPasskey passkey = values.get(index);
                if (passkey.ownerId() == ownerId
                        && passkey.credentialId().equals(credentialId)) {
                    values.set(index, new VaultPasskey(
                            passkey.id(),
                            passkey.ownerId(),
                            passkey.credentialId(),
                            passkey.registrationResponse(),
                            signCount,
                            passkey.createdAt(),
                            usedAt));
                }
            }
        }
    }

    private static final class InMemoryChallengeRepository
            implements VaultChallengeRepository {

        private final AtomicLong ids = new AtomicLong();
        private final List<VaultChallenge> values = new ArrayList<>();

        @Override
        public VaultChallenge save(VaultChallenge challenge) {
            VaultChallenge saved = new VaultChallenge(
                    ids.incrementAndGet(),
                    challenge.ownerId(),
                    challenge.purpose(),
                    challenge.challenge(),
                    challenge.expiresAt(),
                    challenge.consumedAt(),
                    challenge.createdAt());
            values.add(saved);
            return saved;
        }

        @Override
        public Optional<VaultChallenge> findLatestActive(
                long ownerId,
                String purpose,
                Instant now) {
            return values.stream()
                    .filter((challenge) ->
                            challenge.ownerId() == ownerId
                                    && challenge.purpose().equals(purpose)
                                    && challenge.consumedAt() == null
                                    && challenge.expiresAt().isAfter(now))
                    .reduce((first, second) -> second);
        }

        @Override
        public void markConsumed(long id, Instant consumedAt) {
            for (int index = 0; index < values.size(); index += 1) {
                VaultChallenge challenge = values.get(index);
                if (challenge.id() == id) {
                    values.set(index, new VaultChallenge(
                            challenge.id(),
                            challenge.ownerId(),
                            challenge.purpose(),
                            challenge.challenge(),
                            challenge.expiresAt(),
                            consumedAt,
                            challenge.createdAt()));
                }
            }
        }

        @Override
        public void deleteExpiredChallenges(long ownerId, Instant now) {
            values.removeIf((challenge) ->
                    challenge.ownerId() == ownerId
                            && !challenge.expiresAt().isAfter(now));
        }
    }

    private static final class InMemoryUnlockRepository
            implements VaultUnlockSessionRepository {

        private final AtomicLong ids = new AtomicLong();
        private final List<VaultUnlockSession> values = new ArrayList<>();

        @Override
        public VaultUnlockSession save(VaultUnlockSession session) {
            VaultUnlockSession saved = new VaultUnlockSession(
                    ids.incrementAndGet(),
                    session.ownerId(),
                    session.tokenHash(),
                    session.expiresAt(),
                    session.createdAt());
            values.add(saved);
            return saved;
        }

        @Override
        public Optional<VaultUnlockSession> findActive(
                String tokenHash,
                Instant now) {
            return values.stream()
                    .filter((session) ->
                            session.tokenHash().equals(tokenHash)
                                    && session.expiresAt().isAfter(now))
                    .findFirst();
        }

        @Override
        public void deleteByTokenHash(String tokenHash) {
            values.removeIf(
                    (session) -> session.tokenHash().equals(tokenHash));
        }

        @Override
        public void deleteExpiredUnlockSessions(long ownerId, Instant now) {
            values.removeIf((session) ->
                    session.ownerId() == ownerId
                            && !session.expiresAt().isAfter(now));
        }
    }
}
