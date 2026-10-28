package com.pydlove.workcove.vault.application;

import com.pydlove.workcove.development.domain.port.CredentialCipher;
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

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class VaultService {

    private static final String DEFAULT_CATEGORY = "未分类";
    private static final String ALL_CATEGORY = "全部";
    private static final int CATEGORY_NAME_LENGTH = 24;
    private static final String PURPOSE_REGISTRATION = "registration";
    private static final String PURPOSE_AUTHENTICATION = "authentication";
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(2);
    private static final int CHALLENGE_BYTES = 32;

    private final VaultEntryRepository entryRepository;
    private final VaultCategoryRepository categoryRepository;
    private final VaultPasskeyRepository passkeyRepository;
    private final VaultChallengeRepository challengeRepository;
    private final VaultUnlockSessionRepository unlockRepository;
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final SessionTokenCodec tokenCodec;
    private final CredentialCipher credentialCipher;
    private final VaultWebAuthnGateway webAuthnGateway;
    private final Clock clock;
    private final Duration unlockTtl;
    private final SecureRandom random = new SecureRandom();

    public VaultService(
            VaultEntryRepository entryRepository,
            VaultCategoryRepository categoryRepository,
            VaultPasskeyRepository passkeyRepository,
            VaultChallengeRepository challengeRepository,
            VaultUnlockSessionRepository unlockRepository,
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            SessionTokenCodec tokenCodec,
            CredentialCipher credentialCipher,
            VaultWebAuthnGateway webAuthnGateway,
            Clock clock,
            Duration unlockTtl) {
        this.entryRepository = Objects.requireNonNull(entryRepository);
        this.categoryRepository = Objects.requireNonNull(categoryRepository);
        this.passkeyRepository = Objects.requireNonNull(passkeyRepository);
        this.challengeRepository = Objects.requireNonNull(challengeRepository);
        this.unlockRepository = Objects.requireNonNull(unlockRepository);
        this.userRepository = Objects.requireNonNull(userRepository);
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
        this.tokenCodec = Objects.requireNonNull(tokenCodec);
        this.credentialCipher = Objects.requireNonNull(credentialCipher);
        this.webAuthnGateway = Objects.requireNonNull(webAuthnGateway);
        this.clock = Objects.requireNonNull(clock);
        if (unlockTtl == null || unlockTtl.isZero() || unlockTtl.isNegative()) {
            throw new DomainException("保险库解锁有效期必须大于零");
        }
        this.unlockTtl = unlockTtl;
    }

    public VaultStatus status(long ownerId, String rawToken) {
        Instant now = clock.instant();
        boolean passkeyEnabled =
                !passkeyRepository.findPasskeysByOwnerId(ownerId).isEmpty();
        if (!usableToken(rawToken)) {
            return new VaultStatus(false, null, passkeyEnabled);
        }
        try {
            VaultUnlockSession session = unlockRepository
                    .findActive(tokenCodec.hash(rawToken).value(), now)
                    .filter((item) -> item.ownerId() == ownerId)
                    .filter((item) -> item.expiresAt().isAfter(now))
                    .orElse(null);
            return new VaultStatus(
                    session != null,
                    session == null ? null : session.expiresAt(),
                    passkeyEnabled);
        } catch (DomainException exception) {
            return new VaultStatus(false, null, passkeyEnabled);
        }
    }

    public UnlockGrant unlockWithPassword(long ownerId, String password) {
        if (password == null || password.isEmpty()) {
            throw new AuthenticationException("请输入登录密码");
        }
        User user = userRepository.findById(UserId.of(ownerId))
                .orElseThrow(() -> new AuthenticationException("登录已失效"));
        if (!passwordHasher.matches(password, user.getPasswordHash())) {
            throw new AuthenticationException("登录密码不正确");
        }
        return issueUnlock(ownerId);
    }

    public void lock(long ownerId, String rawToken) {
        if (!usableToken(rawToken)) return;
        try {
            VaultUnlockSession session = unlockRepository
                    .findActive(tokenCodec.hash(rawToken).value(), clock.instant())
                    .orElse(null);
            if (session != null && session.ownerId() == ownerId) {
                unlockRepository.deleteByTokenHash(session.tokenHash());
            }
        } catch (DomainException ignored) {
            // An invalid or expired token is already equivalent to locked.
        }
    }

    public List<VaultEntryView> entries(long ownerId, String rawToken) {
        requireUnlocked(ownerId, rawToken);
        return entryRepository.findEntriesByOwnerId(ownerId).stream()
                .map(VaultEntryView::from)
                .toList();
    }

    public List<VaultCategoryView> categories(long ownerId, String rawToken) {
        requireUnlocked(ownerId, rawToken);
        return synchronizedCategories(ownerId).stream()
                .map(VaultCategoryView::from)
                .toList();
    }

    public VaultCategoryView addCategory(
            long ownerId,
            String rawToken,
            String rawName) {
        requireUnlocked(ownerId, rawToken);
        String name = categoryName(rawName);
        if (categoryRepository
                .findCategoryByNameAndOwnerId(name, ownerId)
                .isPresent()) {
            throw new DomainException("这个分类已经存在");
        }
        List<VaultCategory> categories =
                categoryRepository.findCategoriesByOwnerId(ownerId);
        int sortOrder = categories.stream()
                .mapToInt(VaultCategory::sortOrder)
                .max()
                .orElse(-1) + 1;
        Instant now = clock.instant();
        VaultCategory saved = categoryRepository.saveCategory(
                new VaultCategory(
                        null,
                        ownerId,
                        name,
                        sortOrder,
                        now,
                        now));
        return VaultCategoryView.from(saved);
    }

    public VaultCategoryView renameCategory(
            long ownerId,
            String rawToken,
            long categoryId,
            String rawName) {
        requireUnlocked(ownerId, rawToken);
        VaultCategory category = categoryRepository
                .findCategoryByIdAndOwnerId(categoryId, ownerId)
                .orElseThrow(() -> new DomainException(
                        "分类不存在或无权访问"));
        String nextName = categoryName(rawName);
        if (category.name().equals(nextName)) {
            return VaultCategoryView.from(category);
        }
        if (categoryRepository
                .findCategoryByNameAndOwnerId(nextName, ownerId)
                .isPresent()) {
            throw new DomainException("这个分类已经存在");
        }
        Instant now = clock.instant();
        categoryRepository.renameCategory(
                ownerId,
                category.id(),
                category.name(),
                nextName,
                now);
        return new VaultCategoryView(
                category.id(),
                nextName,
                category.sortOrder());
    }

    public void deleteCategory(
            long ownerId,
            String rawToken,
            long categoryId,
            long fallbackCategoryId) {
        requireUnlocked(ownerId, rawToken);
        List<VaultCategory> categories =
                categoryRepository.findCategoriesByOwnerId(ownerId);
        if (categories.size() <= 1) {
            throw new DomainException("至少保留一个密码分类");
        }
        VaultCategory category = categories.stream()
                .filter((item) -> item.id() == categoryId)
                .findFirst()
                .orElseThrow(() -> new DomainException(
                        "分类不存在或无权访问"));
        VaultCategory fallback = categories.stream()
                .filter((item) -> item.id() == fallbackCategoryId)
                .findFirst()
                .orElseThrow(() -> new DomainException(
                        "请选择有效的分类迁移目标"));
        if (category.id().equals(fallback.id())) {
            throw new DomainException("迁移目标不能是当前分类");
        }
        categoryRepository.deleteCategoryAndReassignEntries(
                ownerId,
                category.id(),
                category.name(),
                fallback.name(),
                clock.instant());
    }

    public String revealPassword(long ownerId, long entryId, String rawToken) {
        requireUnlocked(ownerId, rawToken);
        VaultEntry entry = entryRepository
                .findByIdAndOwnerId(entryId, ownerId)
                .orElseThrow(() -> new DomainException("密码条目不存在或无权访问"));
        return credentialCipher.decrypt(entry.passwordCiphertext());
    }

    public VaultEntryView saveEntry(
            long ownerId,
            String rawToken,
            Long entryId,
            VaultEntryInput input) {
        requireUnlocked(ownerId, rawToken);
        Objects.requireNonNull(input, "密码条目");
        String name = required(input.name(), "条目名称", 100);
        String username = optional(input.username(), "用户名", 255);
        String url = optional(input.url(), "网址", 2048);
        String category = optional(
                input.category(),
                "分类",
                CATEGORY_NAME_LENGTH);
        String notes = optional(input.notes(), "备注", 4000);
        String password = input.password();
        if (password != null && password.length() > 1024) {
            throw new DomainException("密码不能超过 1024 个字符");
        }

        VaultEntry existing = entryId == null
                ? null
                : entryRepository.findByIdAndOwnerId(entryId, ownerId)
                        .orElseThrow(() -> new DomainException(
                                "密码条目不存在或无权访问"));
        if (category.isEmpty()) {
            category = existing == null
                    ? synchronizedCategories(ownerId).stream()
                            .findFirst()
                            .map(VaultCategory::name)
                            .orElse(DEFAULT_CATEGORY)
                    : existing.category();
        }
        if ((password == null || password.isEmpty()) && existing == null) {
            throw new DomainException("请输入要保存的密码");
        }
        ensureCategory(ownerId, category);
        String ciphertext = password == null || password.isEmpty()
                ? existing.passwordCiphertext()
                : credentialCipher.encrypt(password);
        Instant now = clock.instant();
        VaultEntry saved = entryRepository.save(new VaultEntry(
                existing == null ? null : existing.id(),
                ownerId,
                name,
                username,
                ciphertext,
                url,
                category,
                notes,
                existing == null ? now : existing.createdAt(),
                now));
        return VaultEntryView.from(saved);
    }

    public void deleteEntry(long ownerId, long entryId, String rawToken) {
        requireUnlocked(ownerId, rawToken);
        entryRepository.findByIdAndOwnerId(entryId, ownerId)
                .orElseThrow(() -> new DomainException(
                        "密码条目不存在或无权访问"));
        entryRepository.deleteByIdAndOwnerId(entryId, ownerId);
    }

    public RegistrationOptions passkeyRegistrationOptions(
            long ownerId,
            String rawToken) {
        requireUnlocked(ownerId, rawToken);
        String challenge = createChallenge(
                ownerId,
                PURPOSE_REGISTRATION);
        List<String> credentialIds = passkeyRepository
                .findPasskeysByOwnerId(ownerId)
                .stream()
                .map(VaultPasskey::credentialId)
                .toList();
        return new RegistrationOptions(challenge, credentialIds);
    }

    public void finishPasskeyRegistration(
            long ownerId,
            String rawToken,
            String responseJson,
            String origin,
            String rpId) {
        requireUnlocked(ownerId, rawToken);
        VaultChallenge challenge = consumeChallenge(
                ownerId,
                PURPOSE_REGISTRATION);
        VaultWebAuthnGateway.RegistrationVerification verified =
                webAuthnGateway.verifyRegistration(
                        responseJson,
                        decodeChallenge(challenge.challenge()),
                        origin,
                        rpId);
        if (passkeyRepository
                .findByCredentialIdAndOwnerId(verified.credentialId(), ownerId)
                .isPresent()) {
            throw new DomainException("该触控 ID 凭据已经注册");
        }
        Instant now = clock.instant();
        passkeyRepository.save(new VaultPasskey(
                null,
                ownerId,
                verified.credentialId(),
                responseJson,
                verified.signCount(),
                now,
                null));
    }

    public AuthenticationOptions passkeyAuthenticationOptions(long ownerId) {
        List<VaultPasskey> passkeys =
                passkeyRepository.findPasskeysByOwnerId(ownerId);
        if (passkeys.isEmpty()) {
            throw new DomainException("尚未启用触控 ID");
        }
        String challenge = createChallenge(
                ownerId,
                PURPOSE_AUTHENTICATION);
        return new AuthenticationOptions(
                challenge,
                passkeys.stream()
                        .map(VaultPasskey::credentialId)
                        .toList());
    }

    public UnlockGrant finishPasskeyAuthentication(
            long ownerId,
            String credentialId,
            String responseJson,
            String origin,
            String rpId) {
        VaultPasskey passkey = passkeyRepository
                .findByCredentialIdAndOwnerId(credentialId, ownerId)
                .orElseThrow(() -> new AuthenticationException(
                        "触控 ID 凭据无效"));
        VaultChallenge challenge = consumeChallenge(
                ownerId,
                PURPOSE_AUTHENTICATION);
        VaultWebAuthnGateway.AuthenticationVerification verified =
                webAuthnGateway.verifyAuthentication(
                        passkey.registrationResponse(),
                        passkey.credentialId(),
                        passkey.signCount(),
                        responseJson,
                        decodeChallenge(challenge.challenge()),
                        origin,
                        rpId);
        passkeyRepository.updateUsage(
                verified.credentialId(),
                ownerId,
                verified.signCount(),
                clock.instant());
        return issueUnlock(ownerId);
    }

    private List<VaultCategory> synchronizedCategories(long ownerId) {
        List<VaultCategory> stored =
                categoryRepository.findCategoriesByOwnerId(ownerId);
        if (stored.isEmpty()) {
            ensureCategory(ownerId, DEFAULT_CATEGORY);
        }

        Map<String, VaultCategory> known = new LinkedHashMap<>();
        stored.forEach((category) -> known.put(category.name(), category));
        entryRepository.findEntriesByOwnerId(ownerId).stream()
                .map(VaultEntry::category)
                .filter((name) -> !name.isBlank())
                .filter((name) -> !known.containsKey(name))
                .distinct()
                .forEach((name) -> {
                    VaultCategory created = ensureCategory(ownerId, name);
                    known.put(created.name(), created);
                });

        return categoryRepository.findCategoriesByOwnerId(ownerId).stream()
                .sorted(Comparator
                        .comparingInt(VaultCategory::sortOrder)
                        .thenComparing(VaultCategory::id))
                .toList();
    }

    private VaultCategory ensureCategory(long ownerId, String name) {
        return categoryRepository
                .findCategoryByNameAndOwnerId(name, ownerId)
                .orElseGet(() -> {
                    List<VaultCategory> current =
                            categoryRepository.findCategoriesByOwnerId(ownerId);
                    int sortOrder = current.stream()
                            .mapToInt(VaultCategory::sortOrder)
                            .max()
                            .orElse(-1) + 1;
                    Instant now = clock.instant();
                    return categoryRepository.saveCategory(
                            new VaultCategory(
                                    null,
                                    ownerId,
                                    name,
                                    sortOrder,
                                    now,
                                    now));
                });
    }

    private VaultUnlockSession requireUnlocked(
            long ownerId,
            String rawToken) {
        if (!usableToken(rawToken)) {
            throw new AuthenticationException(
                    "密码库已锁定，请使用触控 ID 或登录密码验证");
        }
        Instant now = clock.instant();
        return unlockRepository
                .findActive(tokenCodec.hash(rawToken).value(), now)
                .filter((item) -> item.ownerId() == ownerId)
                .filter((item) -> item.expiresAt().isAfter(now))
                .orElseThrow(() -> new AuthenticationException(
                        "密码库已锁定，请重新验证"));
    }

    private UnlockGrant issueUnlock(long ownerId) {
        Instant now = clock.instant();
        unlockRepository.deleteExpiredUnlockSessions(ownerId, now);
        String rawToken = tokenCodec.generateRawToken();
        VaultUnlockSession session = unlockRepository.save(
                new VaultUnlockSession(
                        null,
                        ownerId,
                        tokenCodec.hash(rawToken).value(),
                        now.plus(unlockTtl),
                        now));
        return new UnlockGrant(rawToken, session.expiresAt());
    }

    private String createChallenge(long ownerId, String purpose) {
        Instant now = clock.instant();
        challengeRepository.deleteExpiredChallenges(ownerId, now);
        byte[] bytes = new byte[CHALLENGE_BYTES];
        random.nextBytes(bytes);
        String challenge = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
        challengeRepository.save(new VaultChallenge(
                null,
                ownerId,
                purpose,
                challenge,
                now.plus(CHALLENGE_TTL),
                null,
                now));
        return challenge;
    }

    private VaultChallenge consumeChallenge(long ownerId, String purpose) {
        Instant now = clock.instant();
        VaultChallenge challenge = challengeRepository
                .findLatestActive(ownerId, purpose, now)
                .orElseThrow(() -> new DomainException(
                        "验证请求已过期，请重新发起"));
        challengeRepository.markConsumed(challenge.id(), now);
        return challenge;
    }

    private static byte[] decodeChallenge(String value) {
        try {
            return Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            throw new DomainException("验证挑战格式无效");
        }
    }

    private static boolean usableToken(String value) {
        return value != null
                && !value.isBlank()
                && value.length() <= 128;
    }

    private static String required(
            String value,
            String label,
            int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new DomainException(label + "不能为空");
        }
        if (normalized.length() > maxLength) {
            throw new DomainException(label + "不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private static String optional(
            String value,
            String label,
            int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > maxLength) {
            throw new DomainException(label + "不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private static String categoryName(String value) {
        String normalized = required(
                value,
                "分类名称",
                CATEGORY_NAME_LENGTH);
        if (ALL_CATEGORY.equals(normalized)) {
            throw new DomainException("“全部”是系统筛选项，请换一个名称");
        }
        return normalized;
    }

    public record UnlockGrant(
            String rawToken,
            Instant expiresAt) {
    }

    public record VaultStatus(
            boolean unlocked,
            Instant expiresAt,
            boolean passkeyEnabled) {
    }

    public record VaultEntryInput(
            String name,
            String username,
            String password,
            String url,
            String category,
            String notes) {
    }

    public record VaultCategoryView(
            long id,
            String name,
            int sortOrder) {

        static VaultCategoryView from(VaultCategory category) {
            return new VaultCategoryView(
                    category.id(),
                    category.name(),
                    category.sortOrder());
        }
    }

    public record VaultEntryView(
            long id,
            String name,
            String username,
            String url,
            String category,
            String notes,
            Instant updatedAt) {

        static VaultEntryView from(VaultEntry entry) {
            return new VaultEntryView(
                    entry.id(),
                    entry.name(),
                    entry.username(),
                    entry.url(),
                    entry.category(),
                    entry.notes(),
                    entry.updatedAt());
        }
    }

    public record RegistrationOptions(
            String challenge,
            List<String> credentialIds) {
    }

    public record AuthenticationOptions(
            String challenge,
            List<String> credentialIds) {
    }
}
