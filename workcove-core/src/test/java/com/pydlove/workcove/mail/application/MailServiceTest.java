package com.pydlove.workcove.mail.application;

import com.pydlove.workcove.development.domain.port.CredentialCipher;
import com.pydlove.workcove.mail.application.command.MailAccountCommand;
import com.pydlove.workcove.mail.application.command.MailRuleCommand;
import com.pydlove.workcove.mail.domain.model.MailAccount;
import com.pydlove.workcove.mail.domain.model.MailConnectionSettings;
import com.pydlove.workcove.mail.domain.model.MailExportedMessage;
import com.pydlove.workcove.mail.domain.model.MailFolder;
import com.pydlove.workcove.mail.domain.model.MailFolderRole;
import com.pydlove.workcove.mail.domain.model.MailMessageDetail;
import com.pydlove.workcove.mail.domain.model.MailMessagePage;
import com.pydlove.workcove.mail.domain.model.MailMessageSummary;
import com.pydlove.workcove.mail.domain.model.MailOutgoingMessage;
import com.pydlove.workcove.mail.domain.model.MailRule;
import com.pydlove.workcove.mail.domain.model.MailRuleMatchMode;
import com.pydlove.workcove.mail.domain.model.MailSyncMessage;
import com.pydlove.workcove.mail.domain.port.MailGateway;
import com.pydlove.workcove.mail.domain.repository.MailAccountRepository;
import com.pydlove.workcove.mail.domain.repository.MailCacheRepository;
import com.pydlove.workcove.mail.domain.repository.MailRuleRepository;
import com.pydlove.workcove.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MailServiceTest {

    private static final long OWNER_ID = 10L;
    private static final String WORKSPACE_ID = "default";
    private static final Instant NOW =
            Instant.parse("2026-10-28T00:00:00Z");

    private final InMemoryRepository repository = new InMemoryRepository();
    private final InMemoryCacheRepository cacheRepository =
            new InMemoryCacheRepository();
    private final InMemoryRuleRepository ruleRepository =
            new InMemoryRuleRepository();
    private final FakeGateway gateway = new FakeGateway();
    private final MailService service = new MailService(
            repository,
            cacheRepository,
            ruleRepository,
            gateway,
            new PassThroughCipher(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void savesAnEncryptedAccountAfterVerifyingBothServers() {
        var account = service.saveAccount(
                OWNER_ID,
                WORKSPACE_ID,
                accountCommand(null, "secret"));

        assertEquals("工作邮箱", account.displayName());
        assertTrue(account.hasPassword());
        assertFalse(account.toString().contains("secret"));
        assertEquals("secret", gateway.verified.password());
        assertEquals(1, repository.accounts.size());
        assertEquals(
                "encrypted:secret",
                repository.accounts.get(0).passwordCiphertext());
    }

    @Test
    void retainsTheStoredPasswordWhenEditingWithoutAReplacement() {
        var created = service.saveAccount(
                OWNER_ID,
                WORKSPACE_ID,
                accountCommand(null, "secret"));

        service.saveAccount(
                OWNER_ID,
                WORKSPACE_ID,
                accountCommand(created.id(), ""));

        assertEquals("secret", gateway.verified.password());
        assertEquals(1, repository.accounts.size());
    }

    @Test
    void keepsAccountsInsideTheirWorkspace() {
        var created = service.saveAccount(
                OWNER_ID,
                WORKSPACE_ID,
                accountCommand(null, "secret"));

        assertThrows(
                DomainException.class,
                () -> service.message(
                        OWNER_ID,
                        "another-workspace",
                        created.id(),
                        "INBOX",
                        "1"));
    }

    @Test
    void repliesToTheServerSideSenderInsteadOfClientSuppliedHeaders() {
        MailAccount account = repository.save(new MailAccount(
                null,
                OWNER_ID,
                WORKSPACE_ID,
                "工作邮箱",
                "me@example.com",
                "me@example.com",
                "encrypted:secret",
                "imap.example.com",
                993,
                true,
                "smtp.example.com",
                465,
                true,
                true,
                NOW,
                NOW));

        service.reply(
                OWNER_ID,
                WORKSPACE_ID,
                account.id(),
                "INBOX",
                "42",
                "已收到，我们会尽快处理。");

        assertEquals("42", gateway.readUid);
        assertEquals("sender@example.com", gateway.replySource.fromAddress());
        assertEquals("已收到，我们会尽快处理。", gateway.replyBody);
    }

    @Test
    void savesRuleThatTargetsACustomFolder() {
        MailAccount account = savedAccount();

        MailRule rule = service.saveRule(
                OWNER_ID,
                WORKSPACE_ID,
                account.id(),
                new MailRuleCommand(
                        null,
                        "项目邮件",
                        "ALL",
                        "sender@example.com",
                        "",
                        false,
                        "待处理",
                        true));

        assertEquals("项目邮件", rule.name());
        assertEquals(MailRuleMatchMode.ALL, rule.matchMode());
        assertEquals("待处理", rule.targetFolder());
        assertTrue(rule.enabled());
    }

    @Test
    void movesMatchingUnreadInboxMailUsingTheFirstRule() {
        MailAccount account = savedAccount();
        ruleRepository.rules.add(new MailRule(
                1L,
                OWNER_ID,
                WORKSPACE_ID,
                account.id(),
                "发件人归档",
                MailRuleMatchMode.ALL,
                "sender@example.com",
                "",
                false,
                "待处理",
                true,
                NOW,
                NOW));

        service.sync(
                OWNER_ID,
                WORKSPACE_ID,
                account.id(),
                "INBOX",
                false);
        MailMessagePage messages = service.messages(
                OWNER_ID,
                WORKSPACE_ID,
                account.id(),
                "INBOX",
                false,
                40,
                0);

        assertTrue(messages.messages().isEmpty());
        assertEquals("42", gateway.movedUid);
        assertEquals("待处理", gateway.movedTarget);
    }

    @Test
    void syncStoresMessagesThatCanThenBeReadFromTheCache() {
        MailAccount account = savedAccount();

        service.sync(
                OWNER_ID,
                WORKSPACE_ID,
                account.id(),
                "INBOX",
                false);

        MailMessagePage page = service.messages(
                OWNER_ID,
                WORKSPACE_ID,
                account.id(),
                "INBOX",
                false,
                40,
                0);
        MailMessageDetail detail = service.message(
                OWNER_ID,
                WORKSPACE_ID,
                account.id(),
                "INBOX",
                "42");

        assertEquals(1, page.total());
        assertEquals("42", page.messages().get(0).uid());
        assertEquals("邮件正文", detail.content());
    }

    @Test
    void fillsMissingPreviewFromTheServerAndCachesTheResult() {
        MailAccount account = savedAccount();
        gateway.syncedPreview = "";

        service.sync(
                OWNER_ID,
                WORKSPACE_ID,
                account.id(),
                "INBOX",
                false);
        gateway.messagesCalls = 0;

        MailMessagePage first = service.messages(
                OWNER_ID,
                WORKSPACE_ID,
                account.id(),
                "INBOX",
                false,
                40,
                0);
        assertEquals("邮件摘要", first.messages().get(0).preview());
        assertEquals(1, gateway.messagesCalls);

        gateway.messagesCalls = 0;
        MailMessagePage second = service.messages(
                OWNER_ID,
                WORKSPACE_ID,
                account.id(),
                "INBOX",
                false,
                40,
                0);
        assertEquals("邮件摘要", second.messages().get(0).preview());
        assertEquals(0, gateway.messagesCalls);
    }

    @Test
    void supportsAnyAndAllRuleConditions() {
        MailMessageSummary message = new MailMessageSummary(
                "INBOX",
                "42",
                "<message@example.com>",
                "Alice",
                "sender@example.com",
                "发布通知",
                NOW,
                "邮件摘要",
                true,
                false,
                1);
        MailRule any = new MailRule(
                1L,
                OWNER_ID,
                WORKSPACE_ID,
                1L,
                "任一条件",
                MailRuleMatchMode.ANY,
                "other@example.com",
                "发布",
                false,
                "待处理",
                true,
                NOW,
                NOW);
        MailRule allWithAttachment = new MailRule(
                2L,
                OWNER_ID,
                WORKSPACE_ID,
                1L,
                "全部条件",
                MailRuleMatchMode.ALL,
                "sender@example.com",
                "发布",
                true,
                "待处理",
                true,
                NOW,
                NOW);

        assertTrue(any.matches(message));
        assertTrue(allWithAttachment.matches(message));
    }

    private static MailAccountCommand accountCommand(
            Long id,
            String password) {
        return new MailAccountCommand(
                id,
                "工作邮箱",
                "me@example.com",
                "me@example.com",
                password,
                "imap.example.com",
                993,
                true,
                "smtp.example.com",
                465,
                true,
                true);
    }

    private MailAccount savedAccount() {
        return repository.save(new MailAccount(
                null,
                OWNER_ID,
                WORKSPACE_ID,
                "工作邮箱",
                "me@example.com",
                "me@example.com",
                "encrypted:secret",
                "imap.example.com",
                993,
                true,
                "smtp.example.com",
                465,
                true,
                true,
                NOW,
                NOW));
    }

    private static final class FakeGateway implements MailGateway {

        private MailConnectionSettings verified;
        private MailMessageDetail replySource;
        private String replyBody = "";
        private String readUid = "";
        private String movedUid = "";
        private String movedTarget = "";
        private String syncedPreview = "邮件摘要";
        private int messagesCalls = 0;

        @Override
        public void verify(MailConnectionSettings settings) {
            verified = settings;
        }

        @Override
        public List<com.pydlove.workcove.mail.domain.model.MailFolder> folders(
                MailConnectionSettings settings) {
            return List.of(
                    new MailFolder(
                            "收件箱",
                            "INBOX",
                            MailFolderRole.INBOX,
                            0,
                            0),
                    new MailFolder(
                            "待处理",
                            "待处理",
                            MailFolderRole.CUSTOM,
                            0,
                            0));
        }

        @Override
        public MailFolder createFolder(
                MailConnectionSettings settings,
                String name) {
            return new MailFolder(
                    name,
                    name,
                    MailFolderRole.CUSTOM,
                    0,
                    0);
        }

        @Override
        public MailMessagePage messages(
                MailConnectionSettings settings,
                String folderPath,
                boolean flagged,
                int limit,
                int offset) {
            messagesCalls += 1;
            if ("42".equals(movedUid)
                    && "INBOX".equals(folderPath)) {
                return new MailMessagePage(List.of(), 0, offset, limit);
            }
            return new MailMessagePage(List.of(new MailMessageSummary(
                    folderPath,
                    "42",
                    "<message@example.com>",
                    "发件人",
                    "sender@example.com",
                    "测试邮件",
                    NOW,
                    "邮件摘要",
                    true,
                    false,
                    0)), 1, offset, limit);
        }

        @Override
        public List<MailSyncMessage> syncMessages(
                MailConnectionSettings settings,
                String folderPath) {
            if ("42".equals(movedUid)
                    && "INBOX".equals(folderPath)) {
                return List.of();
            }
            return List.of(new MailSyncMessage(
                    new MailMessageSummary(
                            folderPath,
                            "42",
                            "<message@example.com>",
                            "发件人",
                            "sender@example.com",
                            "测试邮件",
                            NOW,
                            syncedPreview,
                            true,
                            false,
                            0),
                    new MailMessageDetail(
                            folderPath,
                            "42",
                            "<message@example.com>",
                            "发件人",
                            "sender@example.com",
                            List.of(),
                            List.of(),
                            "测试邮件",
                            NOW,
                            "邮件正文",
                            false,
                            false,
                            List.of())));
        }

        @Override
        public MailMessageDetail message(
                MailConnectionSettings settings,
                String folderPath,
                String uid) {
            readUid = uid;
            return new MailMessageDetail(
                    "INBOX",
                    uid,
                    "<message@example.com>",
                    "发件人",
                    "sender@example.com",
                    List.of(),
                    List.of(),
                    "测试邮件",
                    NOW,
                    "邮件正文",
                    false,
                    false,
                    List.of());
        }

        @Override
        public void reply(
                MailConnectionSettings settings,
                MailMessageDetail source,
                String body) {
            replySource = source;
            replyBody = body;
        }

        @Override
        public void send(
                MailConnectionSettings settings,
                MailOutgoingMessage message) {
        }

        @Override
        public void setStarred(
                MailConnectionSettings settings,
                String folderPath,
                String uid,
                boolean starred) {
        }

        @Override
        public void setSeen(
                MailConnectionSettings settings,
                String folderPath,
                String uid,
                boolean seen) {
        }

        @Override
        public void markAllSeen(
                MailConnectionSettings settings,
                String folderPath) {
        }

        @Override
        public void move(
                MailConnectionSettings settings,
                String folderPath,
                String uid,
                String targetFolderPath) {
            movedUid = uid;
            movedTarget = targetFolderPath;
        }

        @Override
        public MailExportedMessage export(
                MailConnectionSettings settings,
                String folderPath,
                String uid) {
            return new MailExportedMessage(
                    new byte[] {1},
                    "mail.eml");
        }
    }

    private static final class PassThroughCipher
            implements CredentialCipher {

        @Override
        public String encrypt(String rawValue) {
            return "encrypted:" + rawValue;
        }

        @Override
        public String decrypt(String encryptedValue) {
            return encryptedValue.replace("encrypted:", "");
        }
    }

    private static final class InMemoryRepository
            implements MailAccountRepository {

        private final List<MailAccount> accounts = new ArrayList<>();
        private long nextId = 1L;

        @Override
        public List<MailAccount> findAccounts(
                long ownerId,
                String workspaceId) {
            return accounts.stream()
                    .filter((account) ->
                            account.ownerId() == ownerId
                                    && account.workspaceId().equals(workspaceId))
                    .toList();
        }

        @Override
        public Optional<MailAccount> findAccount(
                long ownerId,
                String workspaceId,
                long accountId) {
            return accounts.stream()
                    .filter((account) ->
                            account.id() == accountId
                                    && account.ownerId() == ownerId
                                    && account.workspaceId().equals(workspaceId))
                    .findFirst();
        }

        @Override
        public Optional<MailAccount> findAccountByAddress(
                long ownerId,
                String workspaceId,
                String emailAddress) {
            return accounts.stream()
                    .filter((account) ->
                            account.ownerId() == ownerId
                                    && account.workspaceId().equals(workspaceId)
                                    && account.emailAddress().equals(emailAddress))
                    .findFirst();
        }

        @Override
        public MailAccount save(MailAccount account) {
            MailAccount saved = account.id() == null
                    ? new MailAccount(
                            nextId++,
                            account.ownerId(),
                            account.workspaceId(),
                            account.displayName(),
                            account.emailAddress(),
                            account.username(),
                            account.passwordCiphertext(),
                            account.imapHost(),
                            account.imapPort(),
                            account.imapSsl(),
                            account.smtpHost(),
                            account.smtpPort(),
                            account.smtpSsl(),
                            account.smtpAuth(),
                            account.createdAt(),
                            account.updatedAt())
                    : account;
            accounts.removeIf((item) -> item.id().equals(saved.id()));
            accounts.add(saved);
            return saved;
        }

        @Override
        public void delete(
                long ownerId,
                String workspaceId,
                long accountId) {
            accounts.removeIf((account) ->
                    account.id() == accountId
                            && account.ownerId() == ownerId
                            && account.workspaceId().equals(workspaceId));
        }
    }

    private static final class InMemoryRuleRepository
            implements MailRuleRepository {

        private final List<MailRule> rules = new ArrayList<>();
        private long nextId = 1L;

        @Override
        public List<MailRule> findRules(
                long ownerId,
                String workspaceId,
                long accountId) {
            return rules.stream()
                    .filter((rule) ->
                            rule.ownerId() == ownerId
                                    && rule.workspaceId().equals(workspaceId)
                                    && rule.accountId() == accountId)
                    .toList();
        }

        @Override
        public Optional<MailRule> findRule(
                long ownerId,
                String workspaceId,
                long accountId,
                long ruleId) {
            return rules.stream()
                    .filter((rule) ->
                            rule.id() != null
                                    && rule.id() == ruleId
                                    && rule.ownerId() == ownerId
                                    && rule.workspaceId().equals(workspaceId)
                                    && rule.accountId() == accountId)
                    .findFirst();
        }

        @Override
        public MailRule save(MailRule rule) {
            MailRule saved = rule.id() == null
                    ? new MailRule(
                            nextId++,
                            rule.ownerId(),
                            rule.workspaceId(),
                            rule.accountId(),
                            rule.name(),
                            rule.matchMode(),
                            rule.senderContains(),
                            rule.subjectContains(),
                            rule.requiresAttachment(),
                            rule.targetFolder(),
                            rule.enabled(),
                            rule.createdAt(),
                            rule.updatedAt())
                    : rule;
            rules.removeIf((item) ->
                    item.id() != null && item.id().equals(saved.id()));
            rules.add(saved);
            return saved;
        }

        @Override
        public void delete(
                long ownerId,
                String workspaceId,
                long accountId,
                long ruleId) {
            rules.removeIf((rule) ->
                    rule.id() != null
                            && rule.id() == ruleId
                            && rule.ownerId() == ownerId
                            && rule.workspaceId().equals(workspaceId)
                            && rule.accountId() == accountId);
        }
    }

    private static final class InMemoryCacheRepository
            implements MailCacheRepository {

        private List<MailFolder> folders = List.of();
        private final Map<String, Map<String, MailSyncMessage>> messages =
                new LinkedHashMap<>();

        @Override
        public List<MailFolder> findFolders(
                long ownerId,
                String workspaceId,
                long accountId) {
            return folders;
        }

        @Override
        public void replaceFolders(
                long ownerId,
                String workspaceId,
                long accountId,
                List<MailFolder> folders,
                Instant syncedAt) {
            this.folders = List.copyOf(folders);
        }

        @Override
        public MailMessagePage findMessages(
                long ownerId,
                String workspaceId,
                long accountId,
                String folderPath,
                boolean flagged,
                int limit,
                int offset) {
            List<MailMessageSummary> all = messages.values().stream()
                    .flatMap((items) -> items.values().stream())
                    .map(MailSyncMessage::summary)
                    .filter((message) ->
                            flagged
                                    ? message.starred()
                                    : message.folderPath().equals(folderPath))
                    .sorted((left, right) ->
                            right.receivedAt().compareTo(left.receivedAt()))
                    .toList();
            int start = Math.min(offset, all.size());
            int end = Math.min(start + limit, all.size());
            return new MailMessagePage(
                    all.subList(start, end),
                    all.size(),
                    offset,
                    limit);
        }

        @Override
        public Optional<MailMessageDetail> findMessage(
                long ownerId,
                String workspaceId,
                long accountId,
                String folderPath,
                String uid) {
            return Optional.ofNullable(messages.get(folderPath))
                    .map((items) -> items.get(uid))
                    .map(MailSyncMessage::detail);
        }

        @Override
        public void updateMessageDetail(
                long ownerId,
                String workspaceId,
                long accountId,
                String folderPath,
                MailMessageDetail detail,
                Instant syncedAt) {
            Map<String, MailSyncMessage> items =
                    messages.getOrDefault(folderPath, Map.of());
            MailSyncMessage current = items.get(detail.uid());
            if (current == null) return;
            items.put(
                    detail.uid(),
                    new MailSyncMessage(current.summary(), detail));
        }

        @Override
        public void updateMessagePreview(
                long ownerId,
                String workspaceId,
                long accountId,
                MailMessageSummary summary,
                Instant syncedAt) {
            Map<String, MailSyncMessage> items =
                    messages.getOrDefault(summary.folderPath(), Map.of());
            MailSyncMessage current = items.get(summary.uid());
            if (current == null) return;
            items.put(
                    summary.uid(),
                    new MailSyncMessage(summary, current.detail()));
        }

        @Override
        public void replaceMessages(
                long ownerId,
                String workspaceId,
                long accountId,
                String folderPath,
                List<MailSyncMessage> values,
                Instant syncedAt) {
            Map<String, MailSyncMessage> items = new LinkedHashMap<>();
            for (MailSyncMessage message : values) {
                items.put(message.summary().uid(), message);
            }
            messages.put(folderPath, items);
        }

        @Override
        public void setStarred(
                long ownerId,
                String workspaceId,
                long accountId,
                String folderPath,
                String uid,
                boolean starred) {
            replaceFlag(folderPath, uid, starred, null);
        }

        @Override
        public void setSeen(
                long ownerId,
                String workspaceId,
                long accountId,
                String folderPath,
                String uid,
                boolean seen) {
            replaceFlag(folderPath, uid, null, seen);
        }

        @Override
        public void markAllSeen(
                long ownerId,
                String workspaceId,
                long accountId,
                String folderPath) {
            Map<String, MailSyncMessage> items =
                    messages.getOrDefault(folderPath, Map.of());
            items.replaceAll((uid, message) ->
                    replaceSummary(
                            message,
                            message.summary().starred(),
                            true));
        }

        @Override
        public void deleteMessage(
                long ownerId,
                String workspaceId,
                long accountId,
                String folderPath,
                String uid) {
            messages.getOrDefault(folderPath, Map.of()).remove(uid);
        }

        private void replaceFlag(
                String folderPath,
                String uid,
                Boolean starred,
                Boolean seen) {
            Map<String, MailSyncMessage> items =
                    messages.getOrDefault(folderPath, Map.of());
            MailSyncMessage current = items.get(uid);
            if (current == null) return;
            items.put(uid, replaceSummary(
                    current,
                    starred == null ? current.summary().starred() : starred,
                    seen == null ? !current.summary().unread() : seen));
        }

        private MailSyncMessage replaceSummary(
                MailSyncMessage current,
                boolean starred,
                boolean seen) {
            MailMessageSummary summary = current.summary();
            return new MailSyncMessage(
                    new MailMessageSummary(
                            summary.folderPath(),
                            summary.uid(),
                            summary.messageId(),
                            summary.fromName(),
                            summary.fromAddress(),
                            summary.subject(),
                            summary.receivedAt(),
                            summary.preview(),
                            !seen,
                            starred,
                            summary.attachmentCount()),
                    current.detail());
        }
    }
}
