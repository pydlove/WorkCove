package com.pydlove.workcove.mail.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record MailRule(
        Long id,
        long ownerId,
        String workspaceId,
        long accountId,
        String name,
        MailRuleMatchMode matchMode,
        String senderContains,
        String subjectContains,
        boolean requiresAttachment,
        String targetFolder,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {

    public boolean matches(MailMessageSummary message) {
        if (!enabled || message == null || matchMode == null) {
            return false;
        }

        List<Boolean> conditions = new ArrayList<>(3);
        if (!blank(senderContains)) {
            conditions.add(contains(
                    message.fromName() + " " + message.fromAddress(),
                    senderContains));
        }
        if (!blank(subjectContains)) {
            conditions.add(contains(
                    message.subject(),
                    subjectContains));
        }
        if (requiresAttachment) {
            conditions.add(message.attachmentCount() > 0);
        }
        if (conditions.isEmpty()) {
            return false;
        }
        return matchMode == MailRuleMatchMode.ALL
                ? conditions.stream().allMatch(Boolean.TRUE::equals)
                : conditions.stream().anyMatch(Boolean.TRUE::equals);
    }

    private static boolean contains(String value, String needle) {
        return value != null
                && needle != null
                && value.toLowerCase(Locale.ROOT)
                .contains(needle.trim().toLowerCase(Locale.ROOT));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
