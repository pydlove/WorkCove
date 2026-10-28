package com.pydlove.workcove.mail.application.command;

public record MailRuleCommand(
        Long id,
        String name,
        String matchMode,
        String senderContains,
        String subjectContains,
        boolean requiresAttachment,
        String targetFolder,
        boolean enabled
) {
}
