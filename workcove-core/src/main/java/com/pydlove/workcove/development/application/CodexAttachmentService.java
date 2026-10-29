package com.pydlove.workcove.development.application;

import com.pydlove.workcove.development.domain.model.CodexAttachment;
import com.pydlove.workcove.development.domain.port.CodexAttachmentStore;
import com.pydlove.workcove.shared.domain.DomainException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

public final class CodexAttachmentService {

    public static final int MAX_ATTACHMENTS = 8;
    public static final long MAX_ATTACHMENT_BYTES = 20L * 1024 * 1024;
    private static final Pattern WORKSPACE_ID =
            Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final CodexAttachmentStore store;

    public CodexAttachmentService(CodexAttachmentStore store) {
        this.store = store;
    }

    public CodexAttachment upload(
            long ownerId,
            String workspaceId,
            String originalName,
            String contentType,
            long size,
            InputStream content) {
        if (ownerId <= 0) {
            throw new DomainException("用户会话无效");
        }
        if (size <= 0 || size > MAX_ATTACHMENT_BYTES) {
            throw new DomainException("单个附件不能超过 20MB");
        }
        if (content == null) {
            throw new DomainException("附件内容为空");
        }
        try {
            return store.store(
                    ownerId,
                    safeWorkspaceId(workspaceId),
                    safeName(originalName),
                    safeContentType(contentType),
                    size,
                    content);
        } catch (IOException exception) {
            throw new IllegalStateException("附件保存失败", exception);
        }
    }

    public List<CodexAttachment> resolve(
            long ownerId,
            String workspaceId,
            List<String> attachmentIds) {
        if (ownerId <= 0) {
            throw new DomainException("用户会话无效");
        }
        String scope = safeWorkspaceId(workspaceId);
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> uniqueIds = new LinkedHashSet<>();
        for (String id : attachmentIds) {
            if (id != null && !id.isBlank()) {
                uniqueIds.add(id.trim());
            }
        }
        if (uniqueIds.size() > MAX_ATTACHMENTS) {
            throw new DomainException("一次最多发送 8 个附件");
        }

        List<CodexAttachment> attachments = new ArrayList<>(uniqueIds.size());
        for (String id : uniqueIds) {
            attachments.add(store.find(ownerId, scope, id)
                    .orElseThrow(() ->
                            new DomainException("附件不存在或无权访问")));
        }
        return List.copyOf(attachments);
    }

    private String safeName(String value) {
        String name = value == null ? "" : value.strip();
        try {
            name = Path.of(name).getFileName().toString();
        } catch (InvalidPathException ignored) {
            // Fall through to a stable display name.
        }
        name = name.replace('\0', ' ').replaceAll("[\\r\\n\\t]", " ").strip();
        if (name.isBlank()) {
            name = "attachment";
        }
        return name.length() > 160 ? name.substring(name.length() - 160) : name;
    }

    private String safeContentType(String value) {
        String contentType = value == null ? "" : value.strip().toLowerCase();
        if (contentType.isBlank() || contentType.length() > 120) {
            return "application/octet-stream";
        }
        return contentType;
    }

    private String safeWorkspaceId(String value) {
        String workspaceId = value == null ? "" : value.trim();
        if (!WORKSPACE_ID.matcher(workspaceId).matches()
                || ".".equals(workspaceId)
                || "..".equals(workspaceId)) {
            throw new DomainException("工作空间无效");
        }
        return workspaceId;
    }
}
