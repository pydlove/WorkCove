package com.pydlove.workcove.calendar.domain.model;

import java.time.Instant;

public record CalendarReminderSettings(
        long ownerId,
        String workspaceId,
        boolean defaultInApp,
        boolean defaultEmail,
        int defaultMinutesBefore,
        Instant updatedAt) {
}
