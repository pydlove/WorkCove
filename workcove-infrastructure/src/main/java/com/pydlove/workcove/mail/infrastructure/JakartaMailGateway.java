package com.pydlove.workcove.mail.infrastructure;

import com.pydlove.workcove.mail.domain.model.MailAttachment;
import com.pydlove.workcove.mail.domain.model.MailConnectionSettings;
import com.pydlove.workcove.mail.domain.model.MailExportedMessage;
import com.pydlove.workcove.mail.domain.model.MailFolder;
import com.pydlove.workcove.mail.domain.model.MailFolderRole;
import com.pydlove.workcove.mail.domain.model.MailMessageDetail;
import com.pydlove.workcove.mail.domain.model.MailMessagePage;
import com.pydlove.workcove.mail.domain.model.MailMessageSummary;
import com.pydlove.workcove.mail.domain.model.MailOutgoingMessage;
import com.pydlove.workcove.mail.domain.model.MailRecipient;
import com.pydlove.workcove.mail.domain.model.MailSyncMessage;
import com.pydlove.workcove.mail.domain.port.MailGateway;
import com.pydlove.workcove.identity.infrastructure.mail.WorkCoveMailTls;
import com.pydlove.workcove.shared.domain.DomainException;
import jakarta.mail.Address;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.BodyPart;
import jakarta.mail.FetchProfile;
import jakarta.mail.Flags;
import jakarta.mail.search.FlagTerm;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.Transport;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;
import org.eclipse.angus.mail.imap.IMAPStore;
import org.springframework.mail.javamail.MimeMessageHelper;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLHandshakeException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

public final class JakartaMailGateway implements MailGateway {

    private static final Map<String, String> IMAP_CLIENT_ID = Map.of(
            "name", "WorkCove",
            "version", "0.2.6",
            "vendor", "pydlove");
    private static final int MAX_TEXT_BYTES = 1024 * 1024;
    private static final int MAX_PREVIEW_LENGTH = 180;
    private static final int SYNC_MESSAGE_LIMIT = 200;
    private static final int FETCH_BATCH_SIZE = 40;
    private static final String INBOX = "INBOX";

    private final boolean requireStartTls;
    private final SSLSocketFactory socketFactory;

    public JakartaMailGateway() {
        this(true, null);
    }

    JakartaMailGateway(boolean requireStartTls) {
        this(requireStartTls, null);
    }

    public JakartaMailGateway(
            boolean requireStartTls,
            SSLSocketFactory socketFactory) {
        this.requireStartTls = requireStartTls;
        this.socketFactory = socketFactory;
    }

    @Override
    public void verify(MailConnectionSettings settings) {
        verifyInbox(settings);
        verifySender(settings);
    }

    @Override
    public List<MailFolder> folders(MailConnectionSettings settings) {
        Store store = null;
        try {
            store = openStore(settings);
            return discoverFolders(store);
        } catch (Exception exception) {
            throw mailFailure("无法读取邮件目录", exception);
        } finally {
            close(store);
        }
    }

    @Override
    public MailFolder createFolder(
            MailConnectionSettings settings,
            String name) {
        Store store = null;
        try {
            store = openStore(settings);
            Folder folder = store.getFolder(name);
            if (folder == null) {
                throw new DomainException("邮件服务器不支持创建文件夹");
            }
            if (folder.exists()) {
                throw new DomainException("同名邮件文件夹已经存在");
            }
            if (!folder.create(Folder.HOLDS_MESSAGES)) {
                throw new DomainException("邮件服务器拒绝创建文件夹");
            }
            return new MailFolder(
                    name,
                    folder.getFullName(),
                    MailFolderRole.CUSTOM,
                    0,
                    0);
        } catch (DomainException exception) {
            throw exception;
        } catch (Exception exception) {
            throw mailFailure("无法创建邮件文件夹", exception);
        } finally {
            close(store);
        }
    }

    @Override
    public MailMessagePage messages(
            MailConnectionSettings settings,
            String folderPath,
            boolean flagged,
            int limit,
            int offset) {
        try {
            return flagged
                    ? flaggedMessages(settings, limit, offset)
                    : folderMessages(settings, folderPath, limit, offset);
        } catch (DomainException exception) {
            throw exception;
        } catch (Exception exception) {
            throw mailFailure("无法读取邮件列表", exception);
        }
    }

    @Override
    public List<MailSyncMessage> syncMessages(
            MailConnectionSettings settings,
            String folderPath) {
        try (Mailbox mailbox = openFolder(
                settings,
                folderPath,
                Folder.READ_ONLY)) {
            Folder folder = mailbox.folder();
            if (!(folder instanceof UIDFolder uidFolder)) {
                throw new DomainException("当前邮件服务器不支持 UID 收信");
            }
            int messageCount = folder.getMessageCount();
            if (messageCount <= 0) return List.of();
            int firstMessage = Math.max(
                    1,
                    messageCount - SYNC_MESSAGE_LIMIT + 1);
            Message[] messages = folder.getMessages(
                    firstMessage,
                    messageCount);
            FetchProfile profile = new FetchProfile();
            profile.add(FetchProfile.Item.ENVELOPE);
            profile.add(FetchProfile.Item.FLAGS);
            profile.add(FetchProfile.Item.CONTENT_INFO);
            profile.add(UIDFolder.FetchProfileItem.UID);
            fetchInBatches(folder, messages, profile);

            List<MailSyncMessage> result = new ArrayList<>();
            for (int index = messages.length - 1; index >= 0; index--) {
                Message message = messages[index];
                try {
                    long uid = uidFolder.getUID(message);
                    if (uid < 1) continue;
                    MailAddress from = firstAddress(message.getFrom());
                    Flags flags = message.getFlags();
                    String messageId = safeHeader(message, "Message-ID");
                    String subject = subject(message);
                    Instant receivedAt = receivedAt(message);
                    String uidValue = String.valueOf(uid);
                    result.add(new MailSyncMessage(
                            new MailMessageSummary(
                                    folderPath,
                                    uidValue,
                                    messageId,
                                    from.name(),
                                    from.address(),
                                    subject,
                                    receivedAt,
                                    "",
                                    !flags.contains(Flags.Flag.SEEN),
                                    flags.contains(Flags.Flag.FLAGGED),
                                    0),
                            new MailMessageDetail(
                                    folderPath,
                                    uidValue,
                                    messageId,
                                    from.name(),
                                    from.address(),
                                    List.of(),
                                    List.of(),
                                    subject,
                                    receivedAt,
                                    "",
                                    false,
                                    flags.contains(Flags.Flag.FLAGGED),
                                    List.of())));
                } catch (Exception ignored) {
                    // A single malformed message must not block the mailbox sync.
                }
            }
            return List.copyOf(result);
        } catch (DomainException exception) {
            throw exception;
        } catch (Exception exception) {
            throw mailFailure("无法同步邮件", exception);
        }
    }

    @Override
    public MailMessageDetail message(
            MailConnectionSettings settings,
            String folderPath,
            String uid) {
        try (Mailbox mailbox = openFolder(
                settings,
                folderPath,
                Folder.READ_ONLY)) {
            Message message = messageByUid(mailbox.folder(), uid);
            ParsedContent parsed = extractContent(message);
            MailAddress from = firstAddress(message.getFrom());
            String html = parsed.html().isBlank()
                    ? ""
                    : parsed.html();
            String plain = parsed.text().isBlank()
                    ? preview(parsed)
                    : parsed.text();
            boolean htmlBody = !html.isBlank();
            return new MailMessageDetail(
                    folderPath,
                    uid,
                    safeHeader(message, "Message-ID"),
                    from.name(),
                    from.address(),
                    recipients(message.getRecipients(Message.RecipientType.TO)),
                    recipients(message.getRecipients(Message.RecipientType.CC)),
                    subject(message),
                    receivedAt(message),
                    truncate(htmlBody ? html : plain, MAX_TEXT_BYTES),
                    htmlBody,
                    message.getFlags().contains(Flags.Flag.FLAGGED),
                    List.copyOf(parsed.attachments()));
        } catch (DomainException exception) {
            throw exception;
        } catch (Exception exception) {
            throw mailFailure("无法读取邮件正文", exception);
        }
    }

    @Override
    public void reply(
            MailConnectionSettings settings,
            MailMessageDetail source,
            String body) {
        Session session = session(settings, false);
        String protocol = settings.smtpSsl() ? "smtps" : "smtp";
        Transport transport = null;
        try {
            MimeMessage reply = new MimeMessage(session);
            MimeMessageHelper helper = new MimeMessageHelper(
                    reply,
                    false,
                    StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(
                    settings.emailAddress(),
                    settings.displayName(),
                    StandardCharsets.UTF_8.name()));
            helper.setTo(new InternetAddress(
                    source.fromAddress(),
                    source.fromName(),
                    StandardCharsets.UTF_8.name()));
            helper.setSubject(replySubject(source.subject()));
            helper.setText(body, false);
            String messageId = headerValue(source.messageId());
            if (!messageId.isBlank()) {
                reply.setHeader("In-Reply-To", messageId);
                reply.setHeader("References", messageId);
            }

            transport = session.getTransport(protocol);
            connectTransport(transport, settings);
            transport.sendMessage(reply, reply.getAllRecipients());
        } catch (Exception exception) {
            throw mailFailure("邮件回复失败", exception);
        } finally {
            close(transport);
        }
    }

    @Override
    public void setStarred(
            MailConnectionSettings settings,
            String folderPath,
            String uid,
            boolean starred) {
        try (Mailbox mailbox = openFolder(
                settings,
                folderPath,
                Folder.READ_WRITE)) {
            Message message = messageByUid(mailbox.folder(), uid);
            message.setFlag(Flags.Flag.FLAGGED, starred);
        } catch (DomainException exception) {
            throw exception;
        } catch (Exception exception) {
            throw mailFailure("无法更新邮件星标", exception);
        }
    }

    @Override
    public void setSeen(
            MailConnectionSettings settings,
            String folderPath,
            String uid,
            boolean seen) {
        try (Mailbox mailbox = openFolder(
                settings,
                folderPath,
                Folder.READ_WRITE)) {
            Message message = messageByUid(mailbox.folder(), uid);
            message.setFlag(Flags.Flag.SEEN, seen);
        } catch (DomainException exception) {
            throw exception;
        } catch (Exception exception) {
            throw mailFailure("无法更新邮件已读状态", exception);
        }
    }

    @Override
    public void markAllSeen(
            MailConnectionSettings settings,
            String folderPath) {
        try (Mailbox mailbox = openFolder(
                settings,
                folderPath,
                Folder.READ_WRITE)) {
            Message[] messages = mailbox.folder().getMessages();
            if (messages.length > 0) {
                mailbox.folder().setFlags(
                        messages,
                        new Flags(Flags.Flag.SEEN),
                        true);
            }
        } catch (DomainException exception) {
            throw exception;
        } catch (Exception exception) {
            throw mailFailure("无法将目录标记为已读", exception);
        }
    }

    @Override
    public void move(
            MailConnectionSettings settings,
            String folderPath,
            String uid,
            String targetFolderPath) {
        if (folderPath.equalsIgnoreCase(targetFolderPath)) {
            throw new DomainException("邮件已经在目标目录中");
        }
        try (Mailbox mailbox = openFolder(
                settings,
                folderPath,
                Folder.READ_WRITE)) {
            Folder target = mailbox.store().getFolder(targetFolderPath);
            if (target == null || !target.exists()) {
                throw new DomainException("目标邮件目录不存在");
            }
            boolean opened = false;
            try {
                if (!target.isOpen()) {
                    target.open(Folder.READ_WRITE);
                    opened = true;
                }
                Message message = messageByUid(mailbox.folder(), uid);
                mailbox.folder().copyMessages(
                        new Message[] {message},
                        target);
                message.setFlag(Flags.Flag.DELETED, true);
                mailbox.folder().expunge();
            } finally {
                if (opened) {
                    close(target);
                }
            }
        } catch (DomainException exception) {
            throw exception;
        } catch (Exception exception) {
            throw mailFailure("无法移动邮件", exception);
        }
    }

    @Override
    public MailExportedMessage export(
            MailConnectionSettings settings,
            String folderPath,
            String uid) {
        try (Mailbox mailbox = openFolder(
                settings,
                folderPath,
                Folder.READ_ONLY)) {
            Message message = messageByUid(mailbox.folder(), uid);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            message.writeTo(output);
            byte[] content = output.toByteArray();
            if (content.length > 20 * 1024 * 1024) {
                throw new DomainException("邮件超过 20MB，暂不支持导出");
            }
            return new MailExportedMessage(
                    content,
                    exportFilename(subject(message)));
        } catch (DomainException exception) {
            throw exception;
        } catch (Exception exception) {
            throw mailFailure("无法导出邮件", exception);
        }
    }

    @Override
    public void send(
            MailConnectionSettings settings,
            MailOutgoingMessage outgoing) {
        Session session = session(settings, false);
        String protocol = settings.smtpSsl() ? "smtps" : "smtp";
        Transport transport = null;
        try {
            boolean multipart = !outgoing.htmlBody().isBlank();
            MimeMessage message = new MimeMessage(session);
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    multipart,
                    StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(
                    settings.emailAddress(),
                    settings.displayName(),
                    StandardCharsets.UTF_8.name()));
            helper.setTo(internetAddresses(outgoing.to()));
            if (!outgoing.cc().isEmpty()) {
                helper.setCc(internetAddresses(outgoing.cc()));
            }
            helper.setSubject(outgoing.subject());
            String plain = outgoing.plainBody().isBlank()
                    ? outgoing.htmlBody().replaceAll("<[^>]+>", " ")
                    : outgoing.plainBody();
            if (multipart) {
                helper.setText(plain, outgoing.htmlBody());
            } else {
                helper.setText(plain, false);
            }
            String inReplyTo = headerValue(outgoing.inReplyTo());
            if (!inReplyTo.isBlank()) {
                message.setHeader("In-Reply-To", inReplyTo);
                message.setHeader("References", inReplyTo);
            }
            if (outgoing.readReceipt()) {
                message.setHeader(
                        "Disposition-Notification-To",
                        settings.emailAddress());
            }
            transport = session.getTransport(protocol);
            connectTransport(transport, settings);
            transport.sendMessage(message, message.getAllRecipients());
        } catch (Exception exception) {
            throw mailFailure("邮件发送失败", exception);
        } finally {
            close(transport);
        }
    }

    private void verifyInbox(MailConnectionSettings settings) {
        try (Mailbox ignored = openFolder(
                settings,
                INBOX,
                Folder.READ_ONLY)) {
            // 成功打开 INBOX 即表示收件配置可用。
        } catch (DomainException exception) {
            throw exception;
        } catch (Exception exception) {
            throw mailFailure("收件连接失败，请检查服务器、端口和授权码", exception);
        }
    }

    private void verifySender(MailConnectionSettings settings) {
        Session session = session(settings, false);
        String protocol = settings.smtpSsl() ? "smtps" : "smtp";
        Transport transport = null;
        try {
            transport = session.getTransport(protocol);
            connectTransport(transport, settings);
        } catch (Exception exception) {
            throw mailFailure("发件连接失败，请检查服务器、端口和授权码", exception);
        } finally {
            close(transport);
        }
    }

    private Mailbox openFolder(
            MailConnectionSettings settings,
            String folderPath,
            int mode) throws MessagingException {
        Store store = openStore(settings);
        try {
            Folder folder = store.getFolder(folderPath);
            if (folder == null || !folder.exists()) {
                throw new MessagingException("邮件目录不存在");
            }
            folder.open(mode);
            return new Mailbox(store, folder);
        } catch (MessagingException exception) {
            close(store);
            throw exception;
        }
    }

    private Store openStore(MailConnectionSettings settings)
            throws MessagingException {
        Session session = session(settings, true);
        String protocol = settings.imapSsl() ? "imaps" : "imap";
        Store store = session.getStore(protocol);
        try {
            store.connect(
                    settings.imapHost(),
                    settings.imapPort(),
                    settings.username(),
                    settings.password());
            identifyNeteaseClient(store, settings);
            return store;
        } catch (MessagingException exception) {
            close(store);
            throw exception;
        }
    }

    private static void identifyNeteaseClient(
            Store store,
            MailConnectionSettings settings) throws MessagingException {
        if (!(store instanceof IMAPStore imapStore)
                || !isNeteaseImapHost(settings.imapHost())) {
            return;
        }
        imapStore.id(IMAP_CLIENT_ID);
    }

    private static boolean isNeteaseImapHost(String host) {
        String value = host == null
                ? ""
                : host.trim().toLowerCase(Locale.ROOT);
        return value.equals("imap.163.com")
                || value.equals("imap.126.com")
                || value.equals("imap.yeah.net");
    }

    private List<MailFolder> discoverFolders(Store store)
            throws MessagingException {
        List<MailFolder> result = new ArrayList<>();
        result.add(new MailFolder(
                "星标邮件",
                "__FLAGGED__",
                MailFolderRole.FLAGGED,
                0,
                0));

        Folder[] folders = store.getDefaultFolder().list("*");
        boolean inboxFound = false;
        for (Folder folder : folders) {
            if ((folder.getType() & Folder.HOLDS_MESSAGES) == 0) continue;
            String path = folder.getFullName();
            if (path.equalsIgnoreCase(INBOX)) inboxFound = true;
            MailFolderRole role = folderRole(path);
            int unread = 0;
            int total = 0;
            try {
                folder.open(Folder.READ_ONLY);
                unread = Math.max(0, folder.getUnreadMessageCount());
                total = Math.max(0, folder.getMessageCount());
            } catch (MessagingException ignored) {
                continue;
            } finally {
                close(folder);
            }
            result.add(new MailFolder(
                    folderLabel(folder.getName(), role),
                    path,
                    role,
                    unread,
                    total));
        }

        if (!inboxFound) {
            Folder inbox = store.getFolder(INBOX);
            try {
                inbox.open(Folder.READ_ONLY);
                result.add(new MailFolder(
                        "收件箱",
                        INBOX,
                        MailFolderRole.INBOX,
                        Math.max(0, inbox.getUnreadMessageCount()),
                        Math.max(0, inbox.getMessageCount())));
            } finally {
                close(inbox);
            }
        }
        result.sort(Comparator
                .comparingInt((MailFolder folder) ->
                        folderOrder(folder.role()))
                .thenComparing(MailFolder::name));
        return List.copyOf(result);
    }

    private MailMessagePage folderMessages(
            MailConnectionSettings settings,
            String folderPath,
            int limit,
            int offset) throws MessagingException, IOException {
        try (Mailbox mailbox = openFolder(
                settings,
                folderPath,
                Folder.READ_ONLY)) {
            int total = mailbox.folder().getMessageCount();
            int end = total - offset;
            if (end < 1) {
                return new MailMessagePage(
                        List.of(),
                        total,
                        offset,
                        limit);
            }
            int first = Math.max(1, end - limit + 1);
            return new MailMessagePage(
                    summaries(
                            mailbox.folder(),
                            mailbox.folder().getMessages(first, end),
                            limit),
                    total,
                    offset,
                    limit);
        }
    }

    private MailMessagePage flaggedMessages(
            MailConnectionSettings settings,
            int limit,
            int offset) throws MessagingException, IOException {
        Store store = openStore(settings);
        List<MailMessageSummary> result = new ArrayList<>();
        try {
            Folder[] folders = store.getDefaultFolder().list("*");
            for (Folder folder : folders) {
                if ((folder.getType() & Folder.HOLDS_MESSAGES) == 0) continue;
                try {
                    folder.open(Folder.READ_ONLY);
                    Message[] messages = folder.search(new FlagTerm(
                            new Flags(Flags.Flag.FLAGGED),
                            true));
                    result.addAll(summaries(
                            folder,
                            messages,
                            Integer.MAX_VALUE));
                } catch (MessagingException ignored) {
                    // 某些系统目录不可选择，不影响其他目录的星标邮件。
                } finally {
                    close(folder);
                }
            }
        } finally {
            close(store);
        }
        result.sort(Comparator.comparing(
                MailMessageSummary::receivedAt).reversed());
        int start = Math.min(offset, result.size());
        int end = Math.min(start + limit, result.size());
        return new MailMessagePage(
                List.copyOf(result.subList(start, end)),
                result.size(),
                offset,
                limit);
    }

    private List<MailMessageSummary> summaries(
            Folder folder,
            Message[] selected,
            int limit) throws MessagingException, IOException {
        if (!(folder instanceof UIDFolder uidFolder)) {
            throw new DomainException("当前邮件服务器不支持 UID 收信");
        }
        int count = folder.getMessageCount();
        if (count == 0) return List.of();
        Message[] messages = selected;
        if (messages == null) {
            int first = Math.max(1, count - limit + 1);
            messages = folder.getMessages(first, count);
        } else if (messages.length > limit) {
            Message[] tail = new Message[limit];
            System.arraycopy(
                    messages,
                    messages.length - limit,
                    tail,
                    0,
                    limit);
            messages = tail;
        }
        FetchProfile profile = new FetchProfile();
        profile.add(FetchProfile.Item.ENVELOPE);
        profile.add(FetchProfile.Item.FLAGS);
        profile.add(FetchProfile.Item.CONTENT_INFO);
        profile.add(UIDFolder.FetchProfileItem.UID);
        fetchInBatches(folder, messages, profile);

        List<MailMessageSummary> result = new ArrayList<>();
        for (int index = messages.length - 1; index >= 0; index--) {
            Message message = messages[index];
            try {
                long uid = uidFolder.getUID(message);
                if (uid < 1) continue;
                ParsedContent parsed;
                try {
                    parsed = extractContent(message);
                } catch (Exception ignored) {
                    parsed = new ParsedContent("", "", List.of());
                }
                MailAddress from = firstAddress(message.getFrom());
                Flags flags = message.getFlags();
                result.add(new MailMessageSummary(
                        folder.getFullName(),
                        String.valueOf(uid),
                        safeHeader(message, "Message-ID"),
                        from.name(),
                        from.address(),
                        subject(message),
                        receivedAt(message),
                        preview(parsed),
                        !flags.contains(Flags.Flag.SEEN),
                        flags.contains(Flags.Flag.FLAGGED),
                        parsed.attachments().size()));
            } catch (Exception ignored) {
                // Keep the remaining messages available in the list.
            }
        }
        return result;
    }

    private static void fetchInBatches(
            Folder folder,
            Message[] messages,
            FetchProfile profile) {
        for (int start = 0; start < messages.length; start += FETCH_BATCH_SIZE) {
            int end = Math.min(messages.length, start + FETCH_BATCH_SIZE);
            Message[] batch = new Message[end - start];
            System.arraycopy(messages, start, batch, 0, batch.length);
            try {
                folder.fetch(batch, profile);
            } catch (MessagingException ignored) {
                // Individual message reads below can still succeed lazily.
            }
        }
    }

    private static MailFolderRole folderRole(String path) {
        String value = path.toLowerCase();
        if ("inbox".equals(value)) return MailFolderRole.INBOX;
        if (value.contains("sent")
                || value.contains("已发送")
                || value.contains("发件箱")) {
            return MailFolderRole.SENT;
        }
        if (value.contains("draft")
                || value.contains("草稿")) {
            return MailFolderRole.DRAFTS;
        }
        if (value.contains("trash")
                || value.contains("deleted")
                || value.contains("已删除")) {
            return MailFolderRole.TRASH;
        }
        if (value.contains("junk")
                || value.contains("spam")
                || value.contains("垃圾")) {
            return MailFolderRole.JUNK;
        }
        return MailFolderRole.CUSTOM;
    }

    private static String folderLabel(
            String rawName,
            MailFolderRole role) {
        return switch (role) {
            case INBOX -> "收件箱";
            case FLAGGED -> "星标邮件";
            case SENT -> "已发送";
            case DRAFTS -> "草稿";
            case TRASH -> "已删除";
            case JUNK -> "垃圾箱";
            case CUSTOM -> rawName;
        };
    }

    private static int folderOrder(MailFolderRole role) {
        return switch (role) {
            case INBOX -> 0;
            case FLAGGED -> 1;
            case SENT -> 2;
            case DRAFTS -> 3;
            case TRASH -> 4;
            case JUNK -> 5;
            case CUSTOM -> 6;
        };
    }

    private Session session(
            MailConnectionSettings settings,
            boolean inbox) {
        Properties properties = new Properties();
        properties.put("mail.mime.decodetext.strict", "false");
        if (inbox) {
            String prefix = settings.imapSsl() ? "mail.imaps." : "mail.imap.";
            properties.put(
                    prefix + "auth",
                    "true");
            properties.put(prefix + "connectiontimeout", "15000");
            properties.put(prefix + "timeout", "25000");
            properties.put(prefix + "writetimeout", "15000");
            properties.put(prefix + "ssl.checkserveridentity", "true");
            applySocketFactory(properties, prefix);
            if (settings.imapSsl()) {
                properties.put(prefix + "ssl.enable", "true");
            } else {
                properties.put(
                        prefix + "starttls.enable",
                        Boolean.toString(requireStartTls));
                properties.put(
                        prefix + "starttls.required",
                        Boolean.toString(requireStartTls));
            }
        } else {
            String prefix = settings.smtpSsl() ? "mail.smtps." : "mail.smtp.";
            properties.put(
                    prefix + "auth",
                    Boolean.toString(settings.smtpAuth()));
            properties.put(prefix + "connectiontimeout", "15000");
            properties.put(prefix + "timeout", "25000");
            properties.put(prefix + "writetimeout", "15000");
            properties.put(prefix + "ssl.checkserveridentity", "true");
            applySocketFactory(properties, prefix);
            if (settings.smtpSsl()) {
                properties.put(prefix + "ssl.enable", "true");
            } else {
                properties.put(
                        prefix + "starttls.enable",
                        Boolean.toString(requireStartTls));
                properties.put(
                        prefix + "starttls.required",
                        Boolean.toString(requireStartTls));
            }
        }
        return Session.getInstance(properties);
    }

    private static void connectTransport(
            Transport transport,
            MailConnectionSettings settings) throws MessagingException {
        if (settings.smtpAuth()) {
            transport.connect(
                    settings.smtpHost(),
                    settings.smtpPort(),
                    settings.username(),
                    settings.password());
            return;
        }
        transport.connect(
                settings.smtpHost(),
                settings.smtpPort(),
                null,
                null);
    }

    private static InternetAddress[] internetAddresses(
            List<com.pydlove.workcove.mail.domain.model.MailRecipient> values)
            throws java.io.UnsupportedEncodingException {
        return values.stream()
                .map((recipient) -> {
                    try {
                        return new InternetAddress(
                                recipient.address(),
                                recipient.name(),
                                StandardCharsets.UTF_8.name());
                    } catch (java.io.UnsupportedEncodingException exception) {
                        throw new IllegalArgumentException(exception);
                    }
                })
                .toArray(InternetAddress[]::new);
    }

    private void applySocketFactory(
            Properties properties,
            String prefix) {
        if (socketFactory != null) {
            properties.put(
                    prefix + "ssl.socketFactory",
                    socketFactory);
        }
    }

    private static Message messageByUid(Folder folder, String uid)
            throws MessagingException {
        if (!(folder instanceof UIDFolder uidFolder)) {
            throw new DomainException("当前邮件服务器不支持 UID 收信");
        }
        long value;
        try {
            value = Long.parseLong(uid);
        } catch (NumberFormatException exception) {
            throw new DomainException("邮件编号无效");
        }
        Message message = uidFolder.getMessageByUID(value);
        if (message == null) {
            throw new DomainException("邮件不存在或已被移动");
        }
        return message;
    }

    private static ParsedContent extractContent(Part part)
            throws MessagingException, IOException {
        Extraction extraction = new Extraction();
        extract(part, extraction, 0);
        return new ParsedContent(
                extraction.plain.toString(),
                extraction.html.toString(),
                List.copyOf(extraction.attachments));
    }

    private static void extract(
            Part part,
            Extraction extraction,
            int depth) throws MessagingException, IOException {
        if (depth > 12) return;
        String disposition = part.getDisposition();
        String filename = decodedFilename(part.getFileName());
        if (Part.ATTACHMENT.equalsIgnoreCase(disposition)
                || (!filename.isBlank()
                && !part.isMimeType("text/plain")
                && !part.isMimeType("text/html"))) {
            extraction.attachments.add(new MailAttachment(
                    filename,
                    part.getContentType(),
                    Math.max(0, part.getSize())));
            return;
        }
        if (part.isMimeType("text/plain")) {
            String value = readText(part);
            if (!value.isBlank()) {
                extraction.plain.append(value);
            }
            return;
        }
        if (part.isMimeType("text/html")) {
            String value = readText(part);
            if (!value.isBlank()) {
                extraction.html.append(value);
            }
            return;
        }
        Object content = part.getContent();
        if (content instanceof Multipart multipart) {
            for (int index = 0; index < multipart.getCount(); index++) {
                BodyPart child = multipart.getBodyPart(index);
                extract(child, extraction, depth + 1);
            }
        }
    }

    private static String readText(Part part)
            throws MessagingException, IOException {
        try (InputStream input = part.getInputStream()) {
            byte[] bytes = input.readNBytes(MAX_TEXT_BYTES + 1);
            int length = Math.min(bytes.length, MAX_TEXT_BYTES);
            return new String(
                    bytes,
                    0,
                    length,
                    charset(part)).trim();
        }
    }

    private static Charset charset(Part part) throws MessagingException {
        try {
            ContentType contentType = new ContentType(part.getContentType());
            String name = contentType.getParameter("charset");
            return name == null || name.isBlank()
                    ? StandardCharsets.UTF_8
                    : Charset.forName(name.trim());
        } catch (Exception ignored) {
            return StandardCharsets.UTF_8;
        }
    }

    private static String preview(ParsedContent parsed) {
        String value = parsed.text().isBlank()
                ? parsed.html().replaceAll("<[^>]+>", " ")
                : parsed.text();
        return truncate(
                value.replaceAll("\\s+", " ").trim(),
                MAX_PREVIEW_LENGTH);
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static MailAddress firstAddress(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return new MailAddress("", "");
        }
        Address address = addresses[0];
        if (address instanceof InternetAddress internetAddress) {
            return new MailAddress(
                    internetAddress.getPersonal() == null
                            ? ""
                            : internetAddress.getPersonal(),
                    internetAddress.getAddress() == null
                            ? ""
                            : internetAddress.getAddress());
        }
        String value = address.toString();
        int angle = value.lastIndexOf('<');
        if (angle >= 0 && value.endsWith(">")) {
            return new MailAddress(
                    value.substring(0, angle).trim(),
                    value.substring(angle + 1, value.length() - 1));
        }
        return new MailAddress("", value);
    }

    private static List<MailRecipient> recipients(Address[] addresses) {
        if (addresses == null || addresses.length == 0) return List.of();
        List<MailRecipient> result = new ArrayList<>();
        for (Address address : addresses) {
            MailAddress internet = firstAddress(new Address[] {address});
            String value = internet.address();
            if (value != null && !value.isBlank()) {
                result.add(new MailRecipient(
                        internet.name(),
                        value));
            }
        }
        return result;
    }

    private static String subject(Message message)
            throws MessagingException {
        String value = message.getSubject();
        return value == null ? "(无主题)" : value.trim();
    }

    private static Instant receivedAt(Message message)
            throws MessagingException {
        Date date = message.getReceivedDate();
        if (date == null) date = message.getSentDate();
        return date == null ? Instant.EPOCH : date.toInstant();
    }

    private static String safeHeader(Message message, String name) {
        try {
            String[] values = message.getHeader(name);
            return values == null || values.length == 0
                    ? ""
                    : headerValue(values[0]);
        } catch (MessagingException exception) {
            return "";
        }
    }

    private static String decodedFilename(String filename) {
        if (filename == null || filename.isBlank()) return "";
        try {
            return MimeUtility.decodeText(filename);
        } catch (Exception ignored) {
            return filename;
        }
    }

    private static String headerValue(String value) {
        if (value == null) return "";
        String normalized = value.replace("\r", "").replace("\n", "").trim();
        return normalized.length() > 998
                ? normalized.substring(0, 998)
                : normalized;
    }

    private static String replySubject(String subject) {
        String value = subject == null ? "" : subject.trim();
        if (value.regionMatches(true, 0, "Re:", 0, 3)) {
            return value;
        }
        return "Re: " + (value.isBlank() ? "(无主题)" : value);
    }

    private static String exportFilename(String subject) {
        String value = subject == null ? "" : subject.trim();
        value = value.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_");
        if (value.isBlank()) value = "mail";
        if (value.length() > 80) value = value.substring(0, 80);
        return value + ".eml";
    }

    private static DomainException mailFailure(
            String message,
            Exception exception) {
        Throwable root = rootCause(exception);
        if (hasCause(exception, AuthenticationFailedException.class)) {
            return new DomainException(
                    message + "：账号或客户端授权密码被服务器拒绝",
                    exception);
        }
        if (hasCause(exception, SSLHandshakeException.class)) {
            return new DomainException(
                    message + "：TLS 证书校验失败",
                    exception);
        }
        if (root instanceof UnknownHostException) {
            return new DomainException(
                    message + "：找不到服务器地址",
                    exception);
        }
        if (root instanceof SocketTimeoutException) {
            return new DomainException(
                    message + "：连接服务器超时",
                    exception);
        }
        if (root instanceof ConnectException) {
            return new DomainException(
                    message + "：无法连接服务器端口",
                    exception);
        }
        return new DomainException(message + "，请检查邮箱配置", exception);
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null
                && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static boolean hasCause(
            Throwable throwable,
            Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) return true;
            if (current.getCause() == current) break;
            current = current.getCause();
        }
        return false;
    }

    private static void close(Folder folder) {
        if (folder == null) return;
        try {
            if (folder.isOpen()) folder.close(false);
        } catch (MessagingException ignored) {
        }
    }

    private static void close(Store store) {
        if (store == null) return;
        try {
            if (store.isConnected()) store.close();
        } catch (MessagingException ignored) {
        }
    }

    private static void close(Transport transport) {
        if (transport == null) return;
        try {
            if (transport.isConnected()) transport.close();
        } catch (MessagingException ignored) {
        }
    }

    private record ParsedContent(
            String text,
            String html,
            List<MailAttachment> attachments) {
    }

    private record MailAddress(String name, String address) {
    }

    private static final class Extraction {
        private final StringBuilder plain = new StringBuilder();
        private final StringBuilder html = new StringBuilder();
        private final List<MailAttachment> attachments = new ArrayList<>();
    }

    private record Mailbox(Store store, Folder folder)
            implements AutoCloseable {

        @Override
        public void close() {
            JakartaMailGateway.close(folder);
            JakartaMailGateway.close(store);
        }
    }
}
