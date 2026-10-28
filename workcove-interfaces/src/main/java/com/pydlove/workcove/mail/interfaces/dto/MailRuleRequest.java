package com.pydlove.workcove.mail.interfaces.dto;

public record MailRuleRequest(
        Long id,
        String workspaceId,
        long accountId,
        String name,
        String matchMode,
        String senderContains,
        String subjectContains,
        boolean requiresAttachment,
        String targetFolder,
        boolean enabled
) {
}
