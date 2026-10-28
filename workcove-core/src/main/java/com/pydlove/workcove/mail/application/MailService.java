package com.pydlove.workcove.mail.application;

import com.pydlove.workcove.development.domain.port.CredentialCipher;
import com.pydlove.workcove.mail.application.command.MailAccountCommand;
import com.pydlove.workcove.mail.application.command.MailRuleCommand;
import com.pydlove.workcove.mail.application.query.MailAccountView;
import com.pydlove.workcove.mail.domain.model.MailAccount;
import com.pydlove.workcove.mail.domain.model.MailConnectionSettings;
import com.pydlove.workcove.mail.domain.model.MailExportedMessage;
import com.pydlove.workcove.mail.domain.model.MailFolder;
import com.pydlove.workcove.mail.domain.model.MailFolderRole;
import com.pydlove.workcove.mail.domain.model.MailMessageDetail;
import com.pydlove.workcove.mail.domain.model.MailMessagePage;
import com.pydlove.workcove.mail.domain.model.MailMessageSummary;
import com.pydlove.workcove.mail.domain.model.MailOutgoingMessage;
import com.pydlove.workcove.mail.domain.model.MailRecipient;
import com.pydlove.workcove.mail.domain.model.MailRule;
import com.pydlove.workcove.mail.domain.model.MailRuleMatchMode;
import com.pydlove.workcove.mail.domain.model.MailSyncMessage;
import com.pydlove.workcove.mail.domain.model.MailSyncResult;
import com.pydlove.workcove.mail.domain.port.MailGateway;
import com.pydlove.workcove.mail.domain.repository.MailAccountRepository;
import com.pydlove.workcove.mail.domain.repository.MailCacheRepository;
import com.pydlove.workcove.mail.domain.repository.MailRuleRepository;
import com.pydlove.workcove.shared.domain.DomainException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public class MailService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern WORKSPACE_PATTERN = Pattern.compile(
            "[A-Za-z0-9_-]{1,64}");

    private final MailAccountRepository repository;
    private final MailCacheRepository cacheRepository;
    private final MailRuleRepository ruleRepository;
    private final MailGateway gateway;
    private final CredentialCipher credentialCipher;
    private final Clock clock;

    public MailService(
            MailAccountRepository repository,
            MailCacheRepository cacheRepository,
            MailRuleRepository ruleRepository,
            MailGateway gateway,
            CredentialCipher credentialCipher,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.cacheRepository = Objects.requireNonNull(cacheRepository);
        this.ruleRepository = Objects.requireNonNull(ruleRepository);
        this.gateway = Objects.requireNonNull(gateway);
        this.credentialCipher = Objects.requireNonNull(credentialCipher);
        this.clock = Objects.requireNonNull(clock);
    }

    public List<MailAccountView> accounts(
            long ownerId,
            String rawWorkspaceId) {
        String workspaceId = workspaceId(rawWorkspaceId);
        return repository.findAccounts(ownerId, workspaceId).stream()
                .map(MailAccountView::from)
                .toList();
    }

    public MailAccountView saveAccount(
            long ownerId,
            String rawWorkspaceId,
            MailAccountCommand command) {
        String workspaceId = workspaceId(rawWorkspaceId);
        if (command == null) {
            throw new DomainException("请输入邮箱配置");
        }
        String displayName = required(
                command.displayName(),
                "请输入账号名称",
                120);
        if (containsControlCharacter(displayName)) {
            throw new DomainException("账号名称包含无效字符");
        }
        String emailAddress = email(command.emailAddress());
        String username = required(
                command.username(),
                "请输入邮箱用户名",
                320);
        if (containsControlCharacter(username)) {
            throw new DomainException("邮箱用户名包含无效字符");
        }
        String imapHost = host(command.imapHost(), "请输入收件服务器");
        String smtpHost = host(command.smtpHost(), "请输入发件服务器");
        port(command.imapPort(), "收件端口无效");
        port(command.smtpPort(), "发件端口无效");

        MailAccount byAddress = repository.findAccountByAddress(
                ownerId,
                workspaceId,
                emailAddress).orElse(null);
        MailAccount existing = command.id() == null
                ? byAddress
                : repository.findAccount(
                        ownerId,
                        workspaceId,
                        command.id())
                .orElseThrow(() -> new DomainException(
                        "邮箱账号不存在或无权访问"));
        if (byAddress != null
                && existing != null
                && !byAddress.id().equals(existing.id())) {
            throw new DomainException("当前工作空间已添加这个邮箱");
        }
        String password = command.password() == null
                ? ""
                : command.password();
        if (password.isBlank() && existing != null) {
            password = credentialCipher.decrypt(
                    existing.passwordCiphertext());
        }
        if (password.isBlank()) {
            throw new DomainException("请输入邮箱授权码或密码");
        }
        if (password.length() > 1024) {
            throw new DomainException("邮箱密码过长");
        }

        MailConnectionSettings settings = new MailConnectionSettings(
                displayName,
                emailAddress,
                username,
                password,
                imapHost,
                command.imapPort(),
                command.imapSsl(),
                smtpHost,
                command.smtpPort(),
                command.smtpSsl(),
                command.smtpAuth());
        gateway.verify(settings);

        Instant now = clock.instant();
        MailAccount saved = repository.save(new MailAccount(
                existing == null ? null : existing.id(),
                ownerId,
                workspaceId,
                displayName,
                emailAddress,
                username,
                credentialCipher.encrypt(password),
                imapHost,
                command.imapPort(),
                command.imapSsl(),
                smtpHost,
                command.smtpPort(),
                command.smtpSsl(),
                command.smtpAuth(),
                existing == null ? now : existing.createdAt(),
                now));
        return MailAccountView.from(saved);
    }

    public void deleteAccount(
            long ownerId,
            String rawWorkspaceId,
            long accountId) {
        String workspaceId = workspaceId(rawWorkspaceId);
        requireAccount(ownerId, workspaceId, accountId);
        repository.delete(ownerId, workspaceId, accountId);
    }

    public MailMessagePage messages(
            long ownerId,
            String rawWorkspaceId,
            long accountId,
            String rawFolderPath,
            boolean flagged,
            int limit,
            int offset) {
        if (limit < 1 || limit > 200) {
            throw new DomainException("邮件数量必须在 1 到 200 之间");
        }
        if (offset < 0 || offset > 100_000) {
            throw new DomainException("邮件页码无效");
        }
        String workspaceId = workspaceId(rawWorkspaceId);
        String folder = folderPath(rawFolderPath);
        requireAccount(ownerId, workspaceId, accountId);
        MailMessagePage cached = cacheRepository.findMessages(
                ownerId,
                workspaceId,
                accountId,
                folder,
                flagged,
                limit,
                offset);
        if (cached.messages().stream()
                .noneMatch((message) -> message.preview().isBlank())) {
            return cached;
        }
        try {
            MailConnection account = connection(
                    ownerId,
                    workspaceId,
                    accountId);
            MailMessagePage live = gateway.messages(
                    account.settings(),
                    folder,
                    flagged,
                    limit,
                    offset);
            List<MailMessageSummary> enriched = live.messages().stream()
                    .map(MailService::withDisplayPreview)
                    .toList();
            for (MailMessageSummary message : enriched) {
                cacheRepository.updateMessagePreview(
                        ownerId,
                        workspaceId,
                        accountId,
                        message,
                        clock.instant());
            }
            return new MailMessagePage(
                    enriched,
                    live.total(),
                    live.offset(),
                    live.limit());
        } catch (Exception ignored) {
            return cached;
        }
    }

    public List<MailFolder> folders(
            long ownerId,
            String rawWorkspaceId,
            long accountId) {
        String workspaceId = workspaceId(rawWorkspaceId);
        requireAccount(ownerId, workspaceId, accountId);
        return cacheRepository.findFolders(
                ownerId,
                workspaceId,
                accountId);
    }

    public MailSyncResult sync(
            long ownerId,
            String rawWorkspaceId,
            long accountId,
            String rawFolderPath,
            boolean flagged) {
        String workspaceId = workspaceId(rawWorkspaceId);
        MailConnection account = connection(
                ownerId,
                workspaceId,
                accountId);
        String requestedFolder = folderPath(rawFolderPath);
        Instant syncedAt = clock.instant();
        List<MailFolder> folders = gateway.folders(account.settings());
        List<MailFolder> physicalFolders = folders.stream()
                .filter((folder) -> folder.role()
                        != MailFolderRole.FLAGGED)
                .toList();
        List<String> folderPaths;
        if (flagged) {
            folderPaths = physicalFolders.stream()
                    .map(MailFolder::path)
                    .toList();
        } else {
            boolean exists = physicalFolders.stream()
                    .anyMatch((folder) ->
                            folder.path().equals(requestedFolder));
            if (!exists) {
                throw new DomainException("邮件目录不存在，请刷新目录");
            }
            folderPaths = List.of(requestedFolder);
        }

        if (folderPaths.stream().anyMatch(
                (path) -> "INBOX".equalsIgnoreCase(path))) {
            applyInboxRules(
                    ownerId,
                    workspaceId,
                    accountId,
                    account,
                    200);
            folders = gateway.folders(account.settings());
            physicalFolders = folders.stream()
                    .filter((folder) -> folder.role()
                            != MailFolderRole.FLAGGED)
                    .toList();
            if (flagged) {
                folderPaths = physicalFolders.stream()
                        .map(MailFolder::path)
                        .toList();
            }
        }

        cacheRepository.replaceFolders(
                ownerId,
                workspaceId,
                accountId,
                folders,
                syncedAt);
        int messageCount = 0;
        for (String path : folderPaths) {
            List<MailSyncMessage> messages = gateway.syncMessages(
                    account.settings(),
                    path);
            cacheRepository.replaceMessages(
                    ownerId,
                    workspaceId,
                    accountId,
                    path,
                    messages,
                    syncedAt);
            messageCount += messages.size();
        }
        return new MailSyncResult(
                folders.size(),
                messageCount,
                syncedAt);
    }

    public MailFolder createFolder(
            long ownerId,
            String rawWorkspaceId,
            long accountId,
            String rawName) {
        String name = required(rawName, "请输入文件夹名称", 120);
        if (containsControlCharacter(name)
                || name.contains("/")
                || name.contains("\\")) {
            throw new DomainException("文件夹名称包含无效字符");
        }
        MailConnection account = connection(
                ownerId,
                rawWorkspaceId,
                accountId);
        return gateway.createFolder(account.settings(), name);
    }

    public List<MailRule> rules(
            long ownerId,
            String rawWorkspaceId,
            long accountId) {
        String workspaceId = workspaceId(rawWorkspaceId);
        requireAccount(ownerId, workspaceId, accountId);
        return ruleRepository.findRules(
                ownerId,
                workspaceId,
                accountId);
    }

    public MailRule saveRule(
            long ownerId,
            String rawWorkspaceId,
            long accountId,
            MailRuleCommand command) {
        if (command == null) {
            throw new DomainException("请输入邮件规则");
        }
        String workspaceId = workspaceId(rawWorkspaceId);
        MailConnection account = connection(
                ownerId,
                workspaceId,
                accountId);
        String name = required(
                command.name(),
                "请输入规则名称",
                120);
        String senderContains = optionalText(
                command.senderContains(),
                "发件人条件",
                320);
        String subjectContains = optionalText(
                command.subjectContains(),
                "主题条件",
                500);
        if (senderContains.isBlank()
                && subjectContains.isBlank()
                && !command.requiresAttachment()) {
            throw new DomainException("请至少设置一个匹配条件");
        }
        String targetFolder = required(
                command.targetFolder(),
                "请选择目标文件夹",
                1024);
        if ("__FLAGGED__".equals(targetFolder)) {
            throw new DomainException("不能将规则目标设置为星标目录");
        }
        boolean targetExists = gateway.folders(account.settings()).stream()
                .anyMatch((folder) -> folder.path().equals(targetFolder));
        if (!targetExists) {
            throw new DomainException("目标邮件文件夹不存在，请刷新目录后重试");
        }
        MailRule existing = command.id() == null
                ? null
                : ruleRepository.findRule(
                        ownerId,
                        workspaceId,
                        accountId,
                        command.id())
                .orElseThrow(() -> new DomainException(
                        "邮件规则不存在或无权访问"));
        if (existing == null
                && ruleRepository.findRules(
                        ownerId,
                        workspaceId,
                        accountId).size() >= 50) {
            throw new DomainException("每个邮箱最多设置 50 条规则");
        }
        Instant now = clock.instant();
        return ruleRepository.save(new MailRule(
                existing == null ? null : existing.id(),
                ownerId,
                workspaceId,
                accountId,
                name,
                matchMode(command.matchMode()),
                senderContains,
                subjectContains,
                command.requiresAttachment(),
                targetFolder,
                command.enabled(),
                existing == null ? now : existing.createdAt(),
                now));
    }

    public void deleteRule(
            long ownerId,
            String rawWorkspaceId,
            long accountId,
            long ruleId) {
        String workspaceId = workspaceId(rawWorkspaceId);
        requireAccount(ownerId, workspaceId, accountId);
        if (ruleRepository.findRule(
                ownerId,
                workspaceId,
                accountId,
                ruleId).isEmpty()) {
            throw new DomainException("邮件规则不存在或无权访问");
        }
        ruleRepository.delete(
                ownerId,
                workspaceId,
                accountId,
                ruleId);
    }

    public MailMessageDetail message(
            long ownerId,
            String rawWorkspaceId,
            long accountId,
            String rawFolderPath,
            String uid) {
        String value = uid(uid);
        String workspaceId = workspaceId(rawWorkspaceId);
        requireAccount(ownerId, workspaceId, accountId);
        String valueFolderPath = folderPath(rawFolderPath);
        MailMessageDetail cached = cacheRepository.findMessage(
                ownerId,
                workspaceId,
                accountId,
                valueFolderPath,
                value)
                .orElse(null);
        if (cached != null && detailCached(cached)) return cached;
        MailConnection account = connection(
                ownerId,
                workspaceId,
                accountId);
        MailMessageDetail detail = gateway.message(
                account.settings(),
                valueFolderPath,
                value);
        cacheRepository.updateMessageDetail(
                ownerId,
                workspaceId,
                accountId,
                valueFolderPath,
                detail,
                clock.instant());
        return detail;
    }

    public void reply(
            long ownerId,
            String rawWorkspaceId,
            long accountId,
            String rawFolderPath,
            String uid,
            String rawBody) {
        String value = uid(uid);
        String body = required(rawBody, "请输入回复内容", 10_000);
        MailConnection account = connection(
                ownerId,
                rawWorkspaceId,
                accountId);
        String folderPath = folderPath(rawFolderPath);
        MailMessageDetail source = gateway.message(
                account.settings(),
                folderPath,
                value);
        if (source.fromAddress().isBlank()) {
            throw new DomainException("原邮件缺少可回复的发件人地址");
        }
        gateway.reply(account.settings(), source, body);
    }

    public void setStarred(
            long ownerId,
            String rawWorkspaceId,
            long accountId,
            String rawFolderPath,
            String uid,
            boolean starred) {
        String value = uid(uid);
        MailConnection account = connection(
                ownerId,
                rawWorkspaceId,
                accountId);
        gateway.setStarred(
                account.settings(),
                folderPath(rawFolderPath),
                value,
                starred);
        cacheRepository.setStarred(
                ownerId,
                workspaceId(rawWorkspaceId),
                accountId,
                folderPath(rawFolderPath),
                value,
                starred);
    }

    public void send(
            long ownerId,
            String rawWorkspaceId,
            long accountId,
            MailOutgoingMessage message) {
        if (message == null) {
            throw new DomainException("请输入邮件内容");
        }
        List<MailRecipient> to = recipients(
                message.to(),
                "请至少填写一个收件人");
        List<MailRecipient> cc = recipients(message.cc(), "");
        String subject = message.subject() == null
                ? ""
                : message.subject().trim();
        if (subject.length() > 500
                || subject.contains("\n")
                || subject.contains("\r")) {
            throw new DomainException("邮件主题无效");
        }
        String plain = message.plainBody() == null
                ? ""
                : message.plainBody();
        String html = message.htmlBody() == null
                ? ""
                : message.htmlBody();
        if (plain.isBlank() && html.isBlank()) {
            throw new DomainException("请输入邮件正文");
        }
        if (plain.length() > 200_000 || html.length() > 1_000_000) {
            throw new DomainException("邮件正文过长");
        }
        String inReplyTo = message.inReplyTo() == null
                ? ""
                : message.inReplyTo().trim();
        if (inReplyTo.length() > 998
                || inReplyTo.contains("\n")
                || inReplyTo.contains("\r")) {
            throw new DomainException("回复邮件头无效");
        }
        MailConnection account = connection(
                ownerId,
                rawWorkspaceId,
                accountId);
        gateway.send(
                account.settings(),
                new MailOutgoingMessage(
                        to,
                        cc,
                        subject.isBlank() ? "(无主题)" : subject,
                        plain,
                        html,
                        inReplyTo,
                        message.readReceipt()));
    }

    public void setSeen(
            long ownerId,
            String rawWorkspaceId,
            long accountId,
            String rawFolderPath,
            String uid,
            boolean seen) {
        String value = uid(uid);
        MailConnection account = connection(
                ownerId,
                rawWorkspaceId,
                accountId);
        gateway.setSeen(
                account.settings(),
                folderPath(rawFolderPath),
                value,
                seen);
        cacheRepository.setSeen(
                ownerId,
                workspaceId(rawWorkspaceId),
                accountId,
                folderPath(rawFolderPath),
                value,
                seen);
    }

    public void markAllSeen(
            long ownerId,
            String rawWorkspaceId,
            long accountId,
            String rawFolderPath) {
        MailConnection account = connection(
                ownerId,
                rawWorkspaceId,
                accountId);
        gateway.markAllSeen(
                account.settings(),
                folderPath(rawFolderPath));
        cacheRepository.markAllSeen(
                ownerId,
                workspaceId(rawWorkspaceId),
                accountId,
                folderPath(rawFolderPath));
    }

    public void move(
            long ownerId,
            String rawWorkspaceId,
            long accountId,
            String rawFolderPath,
            String uid,
            String rawTargetFolderPath) {
        String value = uid(uid);
        String targetFolder = folderPath(rawTargetFolderPath);
        if ("__FLAGGED__".equals(targetFolder)) {
            throw new DomainException("不能移动到星标虚拟目录");
        }
        MailConnection account = connection(
                ownerId,
                rawWorkspaceId,
                accountId);
        gateway.move(
                account.settings(),
                folderPath(rawFolderPath),
                value,
                targetFolder);
        cacheRepository.deleteMessage(
                ownerId,
                workspaceId(rawWorkspaceId),
                accountId,
                folderPath(rawFolderPath),
                value);
    }

    public MailExportedMessage export(
            long ownerId,
            String rawWorkspaceId,
            long accountId,
            String rawFolderPath,
            String uid) {
        String value = uid(uid);
        MailConnection account = connection(
                ownerId,
                rawWorkspaceId,
                accountId);
        return gateway.export(
                account.settings(),
                folderPath(rawFolderPath),
                value);
    }

    private MailConnection connection(
            long ownerId,
            String rawWorkspaceId,
            long accountId) {
        String workspaceId = workspaceId(rawWorkspaceId);
        MailAccount account = requireAccount(
                ownerId,
                workspaceId,
                accountId);
        String password = credentialCipher.decrypt(
                account.passwordCiphertext());
        return new MailConnection(
                new MailConnectionSettings(
                        account.displayName(),
                        account.emailAddress(),
                        account.username(),
                        password,
                        account.imapHost(),
                        account.imapPort(),
                        account.imapSsl(),
                        account.smtpHost(),
                        account.smtpPort(),
                        account.smtpSsl(),
                        account.smtpAuth()));
    }

    private static boolean detailCached(MailMessageDetail detail) {
        return !detail.content().isBlank()
                || detail.html()
                || !detail.attachments().isEmpty();
    }

    private static MailMessageSummary withDisplayPreview(
            MailMessageSummary message) {
        if (!message.preview().isBlank()) return message;
        String preview = message.attachmentCount() > 0
                ? "包含 " + message.attachmentCount() + " 个附件"
                : "无正文";
        return new MailMessageSummary(
                message.folderPath(),
                message.uid(),
                message.messageId(),
                message.fromName(),
                message.fromAddress(),
                message.subject(),
                message.receivedAt(),
                preview,
                message.unread(),
                message.starred(),
                message.attachmentCount());
    }

    private MailAccount requireAccount(
            long ownerId,
            String workspaceId,
            long accountId) {
        if (accountId <= 0) {
            throw new DomainException("请选择邮箱账号");
        }
        return repository.findAccount(ownerId, workspaceId, accountId)
                .orElseThrow(() -> new DomainException(
                        "邮箱账号不存在或无权访问"));
    }

    private void applyInboxRules(
            long ownerId,
            String workspaceId,
            long accountId,
            MailConnection account,
            int limit) {
        List<MailRule> rules = ruleRepository.findRules(
                        ownerId,
                        workspaceId,
                        accountId).stream()
                .filter(MailRule::enabled)
                .toList();
        if (rules.isEmpty()) {
            return;
        }
        MailMessagePage page = gateway.messages(
                account.settings(),
                "INBOX",
                false,
                limit,
                0);
        for (MailMessageSummary message : page.messages()) {
            if (!message.unread()) {
                continue;
            }
            MailRule matching = rules.stream()
                    .filter((rule) -> rule.matches(message))
                    .filter((rule) -> !rule.targetFolder()
                            .equalsIgnoreCase(message.folderPath()))
                    .findFirst()
                    .orElse(null);
            if (matching == null) {
                continue;
            }
            gateway.move(
                    account.settings(),
                    message.folderPath(),
                    message.uid(),
                    matching.targetFolder());
        }
    }

    private String workspaceId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!WORKSPACE_PATTERN.matcher(normalized).matches()) {
            throw new DomainException("工作空间无效");
        }
        return normalized;
    }

    private String email(String value) {
        String normalized = value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 320
                || !EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new DomainException("邮箱地址无效");
        }
        return normalized;
    }

    private String host(String value, String message) {
        String normalized = value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()
                || normalized.length() > 255
                || normalized.contains("://")
                || normalized.contains("/")
                || normalized.chars().anyMatch(Character::isWhitespace)) {
            throw new DomainException(message);
        }
        return normalized;
    }

    private void port(int value, String message) {
        if (value < 1 || value > 65_535) {
            throw new DomainException(message);
        }
    }

    private String uid(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("[0-9]{1,20}")) {
            throw new DomainException("邮件编号无效");
        }
        return normalized;
    }

    private String folderPath(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) return "INBOX";
        if (normalized.length() > 1024
                || normalized.contains("\n")
                || normalized.contains("\r")) {
            throw new DomainException("邮件目录无效");
        }
        return normalized;
    }

    private String optionalText(
            String value,
            String label,
            int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > maxLength
                || containsControlCharacter(normalized)) {
            throw new DomainException(label + "无效");
        }
        return normalized;
    }

    private MailRuleMatchMode matchMode(String value) {
        String normalized = value == null
                ? "ALL"
                : value.trim().toUpperCase(Locale.ROOT);
        try {
            return MailRuleMatchMode.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new DomainException("邮件规则匹配方式无效");
        }
    }

    private List<MailRecipient> recipients(
            List<MailRecipient> values,
            String emptyMessage) {
        if (values == null || values.isEmpty()) {
            if (!emptyMessage.isBlank()) {
                throw new DomainException(emptyMessage);
            }
            return List.of();
        }
        if (values.size() > 50) {
            throw new DomainException("收件人数量不能超过 50 个");
        }
        return values.stream()
                .map((recipient) -> {
                    if (recipient == null) {
                        throw new DomainException("收件人地址无效");
                    }
                    String name = recipient.name() == null
                            ? ""
                            : recipient.name().trim();
                    if (containsControlCharacter(name)) {
                        throw new DomainException("收件人名称无效");
                    }
                    return new MailRecipient(
                            name,
                            email(recipient.address()));
                })
                .distinct()
                .toList();
    }

    private String required(
            String value,
            String message,
            int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new DomainException(message);
        }
        if (normalized.length() > maxLength) {
            throw new DomainException("输入内容过长");
        }
        return normalized;
    }

    private boolean containsControlCharacter(String value) {
        return value.chars().anyMatch(character ->
                Character.isISOControl(character)
                        && character != '\t');
    }

    private record MailConnection(MailConnectionSettings settings) {
    }
}
