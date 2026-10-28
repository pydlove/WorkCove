package com.pydlove.workcove.calendar.application;

import com.pydlove.workcove.calendar.domain.model.CalendarEvent;
import com.pydlove.workcove.calendar.domain.model.CalendarReminder;
import com.pydlove.workcove.calendar.domain.model.CalendarReminderChannel;
import com.pydlove.workcove.calendar.domain.model.CalendarReminderSettings;
import com.pydlove.workcove.calendar.domain.model.CalendarReminderStatus;
import com.pydlove.workcove.calendar.domain.model.InAppNotification;
import com.pydlove.workcove.calendar.domain.repository.CalendarRepository;
import com.pydlove.workcove.shared.domain.DomainException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class CalendarService {

    public static final ZoneId CALENDAR_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> COLORS =
            Set.of("blue", "green", "orange", "red", "purple");
    private static final Set<Integer> REMINDER_MINUTES =
            Set.of(0, 5, 10, 15, 30, 60, 1440);
    private static final int MAX_NOTIFICATION_LIMIT = 100;

    private final CalendarRepository repository;
    private final Clock clock;

    public CalendarService(CalendarRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);
    }

    public List<CalendarEventView> events(long ownerId) {
        return repository.findEvents(ownerId).stream()
                .map(CalendarEventView::from)
                .toList();
    }

    public CalendarEventView saveEvent(
            long ownerId,
            CalendarEventInput input) {
        Objects.requireNonNull(input, "日程");
        String workspaceId = required(input.workspaceId(), "工作空间", 64);
        String title = required(input.title(), "日程名称", 120);
        String notes = optional(input.notes(), "备注", 2000);
        String color = input.color() == null ? "blue" : input.color().trim();
        if (!COLORS.contains(color)) {
            throw new DomainException("日程颜色无效");
        }
        if (input.date() == null) {
            throw new DomainException("请选择日程日期");
        }
        if (input.startTime() == null) {
            throw new DomainException("请选择开始时间");
        }
        if (input.endTime() != null
                && !input.endTime().isAfter(input.startTime())) {
            throw new DomainException("结束时间必须晚于开始时间");
        }
        int reminderMinutes = input.reminderMinutesBefore();
        if (!REMINDER_MINUTES.contains(reminderMinutes)) {
            throw new DomainException("提醒提前时间无效");
        }
        validateReminderChannels(
                input.reminderInApp(),
                input.reminderEmail(),
                false);

        Instant now = clock.instant();
        String eventKey = input.eventKey() == null
                || input.eventKey().isBlank()
                ? UUID.randomUUID().toString()
                : input.eventKey().trim();
        CalendarEvent existing = repository.findEvent(ownerId, eventKey)
                .orElse(null);
        if (input.eventKey() != null
                && !input.eventKey().isBlank()
                && existing == null) {
            throw new DomainException("日程不存在或无权访问");
        }

        CalendarEvent event = new CalendarEvent(
                existing == null ? null : existing.id(),
                ownerId,
                eventKey,
                workspaceId,
                title,
                input.date(),
                input.startTime(),
                input.endTime(),
                notes,
                color,
                input.reminderInApp(),
                input.reminderEmail(),
                reminderMinutes,
                existing == null ? now : existing.createdAt(),
                now);

        List<CalendarReminder> reminders =
                scheduledReminders(event, now);
        CalendarEvent saved =
                repository.saveEventWithReminders(event, reminders, now);
        return CalendarEventView.from(saved);
    }

    public void deleteEvent(long ownerId, String eventKey) {
        if (eventKey == null || eventKey.isBlank()) {
            throw new DomainException("日程标识不能为空");
        }
        repository.deleteEvent(ownerId, eventKey.trim());
    }

    public CalendarReminderSettingsView reminderSettings(long ownerId) {
        return repository.findReminderSettings(ownerId)
                .map((settings) -> CalendarReminderSettingsView.from(
                        settings,
                        true))
                .orElseGet(() -> new CalendarReminderSettingsView(
                        false,
                        true,
                        false,
                        10));
    }

    public CalendarReminderSettingsView saveReminderSettings(
            long ownerId,
            boolean defaultInApp,
            boolean defaultEmail,
            int defaultMinutesBefore) {
        validateReminderChannels(defaultInApp, defaultEmail, true);
        if (!REMINDER_MINUTES.contains(defaultMinutesBefore)) {
            throw new DomainException("默认提醒提前时间无效");
        }
        CalendarReminderSettings saved =
                repository.saveReminderSettings(
                        new CalendarReminderSettings(
                                ownerId,
                                defaultInApp,
                                defaultEmail,
                                defaultMinutesBefore,
                                clock.instant()));
        return CalendarReminderSettingsView.from(saved, true);
    }

    public List<NotificationView> notifications(long ownerId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_NOTIFICATION_LIMIT));
        return repository.findNotifications(ownerId, safeLimit).stream()
                .map(NotificationView::from)
                .toList();
    }

    public int unreadNotificationCount(long ownerId) {
        return repository.countUnreadNotifications(ownerId);
    }

    public void markNotificationRead(long ownerId, long notificationId) {
        if (notificationId <= 0) {
            throw new DomainException("通知标识无效");
        }
        repository.markNotificationRead(
                ownerId,
                notificationId,
                clock.instant());
    }

    public void markAllNotificationsRead(long ownerId) {
        repository.markAllNotificationsRead(ownerId, clock.instant());
    }

    private List<CalendarReminder> scheduledReminders(
            CalendarEvent event,
            Instant now) {
        Instant eventStart = event.eventDate()
                .atTime(event.startTime())
                .atZone(CALENDAR_ZONE)
                .toInstant();
        if (!eventStart.isAfter(now)) return List.of();

        Instant scheduledAt = eventStart.minus(
                Duration.ofMinutes(event.reminderMinutesBefore()));
        if (scheduledAt.isBefore(now)) {
            scheduledAt = now.plusSeconds(5);
        }

        List<CalendarReminder> reminders = new ArrayList<>();
        if (event.reminderInApp()) {
            reminders.add(newReminder(
                    event,
                    CalendarReminderChannel.IN_APP,
                    scheduledAt,
                    now));
        }
        if (event.reminderEmail()) {
            reminders.add(newReminder(
                    event,
                    CalendarReminderChannel.EMAIL,
                    scheduledAt,
                    now));
        }
        return reminders;
    }

    private static CalendarReminder newReminder(
            CalendarEvent event,
            CalendarReminderChannel channel,
            Instant scheduledAt,
            Instant now) {
        return new CalendarReminder(
                null,
                event.ownerId(),
                event.id() == null ? 0 : event.id(),
                event.eventKey(),
                event.workspaceId(),
                event.title(),
                event.eventDate(),
                event.startTime(),
                channel,
                scheduledAt,
                scheduledAt,
                CalendarReminderStatus.PENDING,
                0,
                null,
                null,
                now,
                now);
    }

    private static void validateReminderChannels(
            boolean inApp,
            boolean email,
            boolean requireOne) {
        if (requireOne && !inApp && !email) {
            throw new DomainException("请至少选择一种提醒方式");
        }
    }

    private static String required(
            String value,
            String label,
            int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new DomainException(label + "不能为空");
        }
        if (normalized.length() > maxLength) {
            throw new DomainException(label + "不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private static String optional(
            String value,
            String label,
            int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > maxLength) {
            throw new DomainException(label + "不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    public record CalendarEventInput(
            String eventKey,
            String workspaceId,
            String title,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            String notes,
            String color,
            boolean reminderInApp,
            boolean reminderEmail,
            int reminderMinutesBefore) {
    }

    public record CalendarEventView(
            String id,
            String workspaceId,
            String title,
            String date,
            String startTime,
            String endTime,
            String notes,
            String color,
            boolean reminderInApp,
            boolean reminderEmail,
            int reminderMinutesBefore,
            Instant createdAt,
            Instant updatedAt) {

        public static CalendarEventView from(CalendarEvent event) {
            return new CalendarEventView(
                    event.eventKey(),
                    event.workspaceId(),
                    event.title(),
                    event.eventDate().toString(),
                    event.startTime().toString(),
                    event.endTime() == null ? "" : event.endTime().toString(),
                    event.notes(),
                    event.color(),
                    event.reminderInApp(),
                    event.reminderEmail(),
                    event.reminderMinutesBefore(),
                    event.createdAt(),
                    event.updatedAt());
        }
    }

    public record CalendarReminderSettingsView(
            boolean configured,
            boolean defaultInApp,
            boolean defaultEmail,
            int defaultMinutesBefore) {

        public static CalendarReminderSettingsView from(
                CalendarReminderSettings settings,
                boolean configured) {
            return new CalendarReminderSettingsView(
                    configured,
                    settings.defaultInApp(),
                    settings.defaultEmail(),
                    settings.defaultMinutesBefore());
        }
    }

    public record NotificationView(
            long id,
            String title,
            String body,
            String linkView,
            Instant readAt,
            Instant createdAt) {

        public static NotificationView from(InAppNotification notification) {
            return new NotificationView(
                    notification.id(),
                    notification.title(),
                    notification.body(),
                    notification.linkView(),
                    notification.readAt(),
                    notification.createdAt());
        }
    }
}
