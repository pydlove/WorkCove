package com.pydlove.workcove.mail.interfaces.dto;

import com.pydlove.workcove.mail.domain.model.MailRule;

import java.time.Instant;

public record MailRuleResponse(
        long id,
        long accountId,
        String name,
        String matchMode,
        String senderContains,
        String subjectContains,
        boolean requiresAttachment,
        String targetFolder,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {

    public static MailRuleResponse from(MailRule rule) {
        return new MailRuleResponse(
                rule.id(),
                rule.accountId(),
                rule.name(),
                rule.matchMode().name(),
                rule.senderContains(),
                rule.subjectContains(),
                rule.requiresAttachment(),
                rule.targetFolder(),
                rule.enabled(),
                rule.createdAt(),
                rule.updatedAt());
    }
}
