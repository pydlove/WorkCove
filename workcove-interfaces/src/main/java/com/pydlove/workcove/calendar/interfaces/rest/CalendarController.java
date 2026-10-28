package com.pydlove.workcove.calendar.interfaces.rest;

import com.pydlove.workcove.calendar.application.CalendarService;
import com.pydlove.workcove.calendar.application.CalendarService.CalendarEventInput;
import com.pydlove.workcove.calendar.application.CalendarService.CalendarEventView;
import com.pydlove.workcove.calendar.application.CalendarService.CalendarReminderSettingsView;
import com.pydlove.workcove.calendar.application.CalendarService.NotificationView;
import com.pydlove.workcove.identity.application.command.AuthService;
import com.pydlove.workcove.identity.interfaces.security.SessionCookieReader;
import com.pydlove.workcove.shared.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequestMapping("/api/calendar")
public class CalendarController {

    private static final String ACTION = "manage-calendar";

    private final CalendarService calendarService;
    private final AuthService authService;
    private final SessionCookieReader sessionCookieReader;

    public CalendarController(
            CalendarService calendarService,
            AuthService authService,
            SessionCookieReader sessionCookieReader) {
        this.calendarService = calendarService;
        this.authService = authService;
        this.sessionCookieReader = sessionCookieReader;
    }

    @GetMapping("/events")
    public List<CalendarEventView> events(HttpServletRequest request) {
        return calendarService.events(currentUserId(request));
    }

    @PostMapping("/events")
    public CalendarEventView createEvent(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @RequestBody CalendarEventRequest body,
            HttpServletRequest request) {
        validateAction(action);
        return calendarService.saveEvent(
                currentUserId(request),
                body.toInput(null));
    }

    @PutMapping("/events/{eventKey}")
    public CalendarEventView updateEvent(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @PathVariable String eventKey,
            @RequestBody CalendarEventRequest body,
            HttpServletRequest request) {
        validateAction(action);
        return calendarService.saveEvent(
                currentUserId(request),
                body.toInput(eventKey));
    }

    @DeleteMapping("/events/{eventKey}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEvent(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @PathVariable String eventKey,
            HttpServletRequest request) {
        validateAction(action);
        calendarService.deleteEvent(
                currentUserId(request),
                eventKey);
    }

    @GetMapping("/reminder-settings")
    public CalendarReminderSettingsView reminderSettings(
            HttpServletRequest request) {
        return calendarService.reminderSettings(currentUserId(request));
    }

    @PutMapping("/reminder-settings")
    public CalendarReminderSettingsView saveReminderSettings(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @RequestBody ReminderSettingsRequest body,
            HttpServletRequest request) {
        validateAction(action);
        return calendarService.saveReminderSettings(
                currentUserId(request),
                body.defaultInApp(),
                body.defaultEmail(),
                body.defaultMinutesBefore());
    }

    @GetMapping("/notifications")
    public List<NotificationView> notifications(
            @RequestParam(defaultValue = "20") int limit,
            HttpServletRequest request) {
        return calendarService.notifications(
                currentUserId(request),
                limit);
    }

    @GetMapping("/notifications/unread-count")
    public UnreadCountResponse unreadCount(HttpServletRequest request) {
        return new UnreadCountResponse(
                calendarService.unreadNotificationCount(
                        currentUserId(request)));
    }

    @PostMapping("/notifications/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markNotificationRead(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @PathVariable long id,
            HttpServletRequest request) {
        validateAction(action);
        calendarService.markNotificationRead(
                currentUserId(request),
                id);
    }

    @PostMapping("/notifications/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllNotificationsRead(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            HttpServletRequest request) {
        validateAction(action);
        calendarService.markAllNotificationsRead(
                currentUserId(request));
    }

    private long currentUserId(HttpServletRequest request) {
        return authService.currentSession(
                sessionCookieReader.readToken(request)).user().id();
    }

    private void validateAction(String action) {
        if (!ACTION.equals(action)) {
            throw new DomainException("不支持的日历操作");
        }
    }

    public record CalendarEventRequest(
            String workspaceId,
            String title,
            String date,
            String startTime,
            String endTime,
            String notes,
            String color,
            Boolean reminderInApp,
            Boolean reminderEmail,
            Integer reminderMinutesBefore) {

        CalendarEventInput toInput(String eventKey) {
            return new CalendarEventInput(
                    eventKey,
                    workspaceId,
                    title,
                    parseDate(date),
                    parseTime(startTime, "开始时间"),
                    parseNullableTime(endTime, "结束时间"),
                    notes,
                    color,
                    reminderInApp == null || reminderInApp,
                    reminderEmail != null && reminderEmail,
                    reminderMinutesBefore == null
                            ? 10
                            : reminderMinutesBefore);
        }
    }

    public record ReminderSettingsRequest(
            boolean defaultInApp,
            boolean defaultEmail,
            int defaultMinutesBefore) {
    }

    public record UnreadCountResponse(int count) {
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainException("请选择日程日期");
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new DomainException("日程日期格式无效");
        }
    }

    private static LocalTime parseTime(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new DomainException("请选择" + label);
        }
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException exception) {
            throw new DomainException(label + "格式无效");
        }
    }

    private static LocalTime parseNullableTime(String value, String label) {
        return value == null || value.isBlank()
                ? null
                : parseTime(value, label);
    }
}
