package com.pydlove.workcove.calendar.domain.model;

import java.time.Instant;

public record CalendarReminderSettings(
        long ownerId,
        boolean defaultInApp,
        boolean defaultEmail,
        int defaultMinutesBefore,
        Instant updatedAt) {
}
