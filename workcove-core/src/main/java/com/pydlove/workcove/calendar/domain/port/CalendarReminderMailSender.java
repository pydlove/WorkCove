package com.pydlove.workcove.calendar.domain.port;

import com.pydlove.workcove.calendar.domain.model.CalendarReminder;
import com.pydlove.workcove.identity.domain.model.Email;

public interface CalendarReminderMailSender {

    void send(Email recipient, CalendarReminder reminder);
}
