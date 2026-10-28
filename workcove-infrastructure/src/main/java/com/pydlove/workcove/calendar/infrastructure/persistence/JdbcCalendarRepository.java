package com.pydlove.workcove.calendar.infrastructure.persistence;

import com.pydlove.workcove.calendar.domain.model.CalendarEvent;
import com.pydlove.workcove.calendar.domain.model.CalendarReminder;
import com.pydlove.workcove.calendar.domain.model.CalendarReminderChannel;
import com.pydlove.workcove.calendar.domain.model.CalendarReminderSettings;
import com.pydlove.workcove.calendar.domain.model.CalendarReminderStatus;
import com.pydlove.workcove.calendar.domain.model.InAppNotification;
import com.pydlove.workcove.calendar.domain.repository.CalendarRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcCalendarRepository implements CalendarRepository {

    private static final RowMapper<CalendarEvent> EVENT_MAPPER =
            JdbcCalendarRepository::mapEvent;
    private static final RowMapper<CalendarReminder> REMINDER_MAPPER =
            JdbcCalendarRepository::mapReminder;
    private static final RowMapper<InAppNotification> NOTIFICATION_MAPPER =
            JdbcCalendarRepository::mapNotification;

    private final JdbcTemplate jdbc;

    public JdbcCalendarRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<CalendarEvent> findEvents(long ownerId) {
        return jdbc.query(
                """
                SELECT id, owner_id, event_key, workspace_id, title,
                       event_date, start_time, end_time, notes, color,
                       reminder_in_app, reminder_email,
                       reminder_minutes_before, created_at, updated_at
                FROM calendar_event
                WHERE owner_id = ?
                ORDER BY event_date, start_time, id
                """,
                EVENT_MAPPER,
                ownerId);
    }

    @Override
    public Optional<CalendarEvent> findEvent(long ownerId, String eventKey) {
        return jdbc.query(
                """
                SELECT id, owner_id, event_key, workspace_id, title,
                       event_date, start_time, end_time, notes, color,
                       reminder_in_app, reminder_email,
                       reminder_minutes_before, created_at, updated_at
                FROM calendar_event
                WHERE owner_id = ? AND event_key = ?
                """,
                EVENT_MAPPER,
                ownerId,
                eventKey).stream().findFirst();
    }

    @Override
    @Transactional
    public CalendarEvent saveEventWithReminders(
            CalendarEvent event,
            List<CalendarReminder> reminders,
            Instant now) {
        long eventId;
        if (event.id() == null) {
            eventId = insertEvent(event);
        } else {
            int updated = jdbc.update(
                    """
                    UPDATE calendar_event
                    SET workspace_id = ?, title = ?, event_date = ?,
                        start_time = ?, end_time = ?, notes = ?, color = ?,
                        reminder_in_app = ?, reminder_email = ?,
                        reminder_minutes_before = ?, updated_at = ?
                    WHERE id = ? AND owner_id = ?
                    """,
                    event.workspaceId(),
                    event.title(),
                    Date.valueOf(event.eventDate()),
                    Time.valueOf(event.startTime()),
                    nullableTime(event.endTime()),
                    event.notes(),
                    event.color(),
                    event.reminderInApp(),
                    event.reminderEmail(),
                    event.reminderMinutesBefore(),
                    timestamp(event.updatedAt()),
                    event.id(),
                    event.ownerId());
            if (updated != 1) {
                throw new IllegalStateException("日程更新失败");
            }
            eventId = event.id();
        }

        jdbc.update(
                """
                DELETE FROM calendar_reminder
                WHERE event_id = ? AND status <> 'SENT'
                """,
                eventId);
        for (CalendarReminder reminder : reminders) {
            jdbc.update(
                    """
                    INSERT INTO calendar_reminder (
                        owner_id, event_id, channel, scheduled_at_utc,
                        available_at_utc, status, attempts, created_at,
                        updated_at
                    )
                    VALUES (?, ?, ?, ?, ?, 'PENDING', 0, ?, ?)
                    ON DUPLICATE KEY UPDATE id = calendar_reminder.id
                    """,
                    event.ownerId(),
                    eventId,
                    reminder.channel().name(),
                    timestamp(reminder.scheduledAt()),
                    timestamp(reminder.availableAt()),
                    timestamp(now),
                    timestamp(now));
        }
        return findEvent(event.ownerId(), event.eventKey())
                .orElseThrow(() -> new IllegalStateException("日程保存失败"));
    }

    @Override
    public void deleteEvent(long ownerId, String eventKey) {
        jdbc.update(
                "DELETE FROM calendar_event WHERE owner_id = ? AND event_key = ?",
                ownerId,
                eventKey);
    }

    @Override
    public Optional<CalendarReminderSettings> findReminderSettings(long ownerId) {
        return jdbc.query(
                """
                SELECT owner_id, default_in_app, default_email,
                       default_minutes_before, updated_at
                FROM calendar_reminder_settings
                WHERE owner_id = ?
                """,
                (result, rowNumber) -> new CalendarReminderSettings(
                        result.getLong("owner_id"),
                        result.getBoolean("default_in_app"),
                        result.getBoolean("default_email"),
                        result.getInt("default_minutes_before"),
                        instant(result, "updated_at")),
                ownerId).stream().findFirst();
    }

    @Override
    public CalendarReminderSettings saveReminderSettings(
            CalendarReminderSettings settings) {
        jdbc.update(
                """
                INSERT INTO calendar_reminder_settings (
                    owner_id, default_in_app, default_email,
                    default_minutes_before, updated_at
                )
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    default_in_app = VALUES(default_in_app),
                    default_email = VALUES(default_email),
                    default_minutes_before = VALUES(default_minutes_before),
                    updated_at = VALUES(updated_at)
                """,
                settings.ownerId(),
                settings.defaultInApp(),
                settings.defaultEmail(),
                settings.defaultMinutesBefore(),
                timestamp(settings.updatedAt()));
        return findReminderSettings(settings.ownerId())
                .orElseThrow(() -> new IllegalStateException("提醒设置保存失败"));
    }

    @Override
    public List<CalendarReminder> findDueReminders(Instant now, int limit) {
        jdbc.update(
                """
                UPDATE calendar_reminder
                SET status = 'PENDING', updated_at = ?
                WHERE status = 'PROCESSING' AND updated_at < ?
                """,
                timestamp(now),
                timestamp(now.minusSeconds(300)));
        return jdbc.query(
                """
                SELECT reminder.id, reminder.owner_id, reminder.event_id,
                       event.event_key, event.workspace_id, event.title,
                       event.event_date, event.start_time, reminder.channel,
                       reminder.scheduled_at_utc, reminder.available_at_utc,
                       reminder.status, reminder.attempts,
                       reminder.sent_at_utc, reminder.last_error,
                       reminder.created_at, reminder.updated_at
                FROM calendar_reminder reminder
                JOIN calendar_event event ON event.id = reminder.event_id
                WHERE reminder.status = 'PENDING'
                  AND reminder.available_at_utc <= ?
                ORDER BY reminder.scheduled_at_utc, reminder.id
                LIMIT ?
                """,
                REMINDER_MAPPER,
                timestamp(now),
                Math.max(1, limit));
    }

    @Override
    public boolean claimReminder(long reminderId, Instant now) {
        return jdbc.update(
                """
                UPDATE calendar_reminder
                SET status = 'PROCESSING',
                    attempts = attempts + 1,
                    updated_at = ?
                WHERE id = ? AND status = 'PENDING'
                """,
                timestamp(now),
                reminderId) == 1;
    }

    @Override
    public void markReminderSent(long reminderId, Instant sentAt) {
        jdbc.update(
                """
                UPDATE calendar_reminder
                SET status = 'SENT', sent_at_utc = ?, last_error = NULL,
                    updated_at = ?
                WHERE id = ?
                """,
                timestamp(sentAt),
                timestamp(sentAt),
                reminderId);
    }

    @Override
    public void markReminderFailed(
            long reminderId,
            String error,
            Instant availableAt,
            boolean terminal,
            Instant now) {
        jdbc.update(
                """
                UPDATE calendar_reminder
                SET status = ?, available_at_utc = ?, last_error = ?,
                    updated_at = ?
                WHERE id = ?
                """,
                terminal
                        ? CalendarReminderStatus.FAILED.name()
                        : CalendarReminderStatus.PENDING.name(),
                timestamp(availableAt),
                error,
                timestamp(now),
                reminderId);
    }

    @Override
    public void createInAppNotification(InAppNotification notification) {
        jdbc.update(
                """
                INSERT INTO in_app_notification (
                    owner_id, workspace_id, event_id, title, body,
                    link_view, dedupe_key, created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE id = in_app_notification.id
                """,
                notification.ownerId(),
                notification.workspaceId(),
                notification.eventId(),
                notification.title(),
                notification.body(),
                notification.linkView(),
                notification.dedupeKey(),
                timestamp(notification.createdAt()));
    }

    @Override
    public List<InAppNotification> findNotifications(long ownerId, int limit) {
        return jdbc.query(
                """
                SELECT id, owner_id, workspace_id, event_id, title, body,
                       link_view, dedupe_key, read_at_utc, created_at
                FROM in_app_notification
                WHERE owner_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT ?
                """,
                NOTIFICATION_MAPPER,
                ownerId,
                Math.max(1, limit));
    }

    @Override
    public int countUnreadNotifications(long ownerId) {
        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM in_app_notification
                WHERE owner_id = ? AND read_at_utc IS NULL
                """,
                Integer.class,
                ownerId);
        return count == null ? 0 : count;
    }

    @Override
    public void markNotificationRead(
            long ownerId,
            long notificationId,
            Instant readAt) {
        jdbc.update(
                """
                UPDATE in_app_notification
                SET read_at_utc = ?
                WHERE id = ? AND owner_id = ? AND read_at_utc IS NULL
                """,
                timestamp(readAt),
                notificationId,
                ownerId);
    }

    @Override
    public void markAllNotificationsRead(long ownerId, Instant readAt) {
        jdbc.update(
                """
                UPDATE in_app_notification
                SET read_at_utc = ?
                WHERE owner_id = ? AND read_at_utc IS NULL
                """,
                timestamp(readAt),
                ownerId);
    }

    private long insertEvent(CalendarEvent event) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update((connection) -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO calendar_event (
                        owner_id, workspace_id, event_key, title, event_date,
                        start_time, end_time, notes, color,
                        reminder_in_app, reminder_email,
                        reminder_minutes_before, created_at, updated_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, event.ownerId());
            statement.setString(2, event.workspaceId());
            statement.setString(3, event.eventKey());
            statement.setString(4, event.title());
            statement.setDate(5, Date.valueOf(event.eventDate()));
            statement.setTime(6, Time.valueOf(event.startTime()));
            statement.setTime(7, nullableTime(event.endTime()));
            statement.setString(8, event.notes());
            statement.setString(9, event.color());
            statement.setBoolean(10, event.reminderInApp());
            statement.setBoolean(11, event.reminderEmail());
            statement.setInt(12, event.reminderMinutesBefore());
            statement.setTimestamp(13, timestamp(event.createdAt()));
            statement.setTimestamp(14, timestamp(event.updatedAt()));
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("日程创建失败");
        }
        return key.longValue();
    }

    private static CalendarEvent mapEvent(
            ResultSet result,
            int rowNumber) throws SQLException {
        return new CalendarEvent(
                result.getLong("id"),
                result.getLong("owner_id"),
                result.getString("event_key"),
                result.getString("workspace_id"),
                result.getString("title"),
                localDate(result, "event_date"),
                localTime(result, "start_time"),
                localTime(result, "end_time"),
                result.getString("notes"),
                result.getString("color"),
                result.getBoolean("reminder_in_app"),
                result.getBoolean("reminder_email"),
                result.getInt("reminder_minutes_before"),
                instant(result, "created_at"),
                instant(result, "updated_at"));
    }

    private static CalendarReminder mapReminder(
            ResultSet result,
            int rowNumber) throws SQLException {
        return new CalendarReminder(
                result.getLong("id"),
                result.getLong("owner_id"),
                result.getLong("event_id"),
                result.getString("event_key"),
                result.getString("workspace_id"),
                result.getString("title"),
                localDate(result, "event_date"),
                localTime(result, "start_time"),
                CalendarReminderChannel.valueOf(result.getString("channel")),
                instant(result, "scheduled_at_utc"),
                instant(result, "available_at_utc"),
                CalendarReminderStatus.valueOf(result.getString("status")),
                result.getInt("attempts"),
                instant(result, "sent_at_utc"),
                result.getString("last_error"),
                instant(result, "created_at"),
                instant(result, "updated_at"));
    }

    private static InAppNotification mapNotification(
            ResultSet result,
            int rowNumber) throws SQLException {
        return new InAppNotification(
                result.getLong("id"),
                result.getLong("owner_id"),
                result.getString("workspace_id"),
                nullableLong(result, "event_id"),
                result.getString("title"),
                result.getString("body"),
                result.getString("link_view"),
                result.getString("dedupe_key"),
                instant(result, "read_at_utc"),
                instant(result, "created_at"));
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Time nullableTime(LocalTime value) {
        return value == null ? null : Time.valueOf(value);
    }

    private static Instant instant(ResultSet result, String column)
            throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static LocalDate localDate(ResultSet result, String column)
            throws SQLException {
        Date value = result.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    private static LocalTime localTime(ResultSet result, String column)
            throws SQLException {
        Time value = result.getTime(column);
        return value == null ? null : value.toLocalTime();
    }

    private static Long nullableLong(ResultSet result, String column)
            throws SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? null : value;
    }
}
