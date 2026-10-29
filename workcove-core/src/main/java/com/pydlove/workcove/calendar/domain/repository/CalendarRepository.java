package com.pydlove.workcove.calendar.domain.repository;

import com.pydlove.workcove.calendar.domain.model.CalendarEvent;
import com.pydlove.workcove.calendar.domain.model.CalendarReminder;
import com.pydlove.workcove.calendar.domain.model.CalendarReminderSettings;
import com.pydlove.workcove.calendar.domain.model.InAppNotification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CalendarRepository {

    List<CalendarEvent> findEvents(long ownerId, String workspaceId);

    Optional<CalendarEvent> findEvent(
            long ownerId,
            String workspaceId,
            String eventKey);

    CalendarEvent saveEventWithReminders(
            CalendarEvent event,
            List<CalendarReminder> reminders,
            Instant now);

    void deleteEvent(long ownerId, String workspaceId, String eventKey);

    Optional<CalendarReminderSettings> findReminderSettings(
            long ownerId,
            String workspaceId);

    CalendarReminderSettings saveReminderSettings(
            CalendarReminderSettings settings);

    List<CalendarReminder> findDueReminders(Instant now, int limit);

    boolean claimReminder(long reminderId, Instant now);

    void markReminderSent(long reminderId, Instant sentAt);

    void markReminderFailed(
            long reminderId,
            String error,
            Instant availableAt,
            boolean terminal,
            Instant now);

    void createInAppNotification(InAppNotification notification);

    List<InAppNotification> findNotifications(
            long ownerId,
            String workspaceId,
            int limit);

    int countUnreadNotifications(long ownerId, String workspaceId);

    void markNotificationRead(
            long ownerId,
            String workspaceId,
            long notificationId,
            Instant readAt);

    void markAllNotificationsRead(
            long ownerId,
            String workspaceId,
            Instant readAt);
}
