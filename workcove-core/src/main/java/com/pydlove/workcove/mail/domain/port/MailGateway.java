package com.pydlove.workcove.mail.domain.port;

import com.pydlove.workcove.mail.domain.model.MailConnectionSettings;
import com.pydlove.workcove.mail.domain.model.MailExportedMessage;
import com.pydlove.workcove.mail.domain.model.MailFolder;
import com.pydlove.workcove.mail.domain.model.MailMessageDetail;
import com.pydlove.workcove.mail.domain.model.MailMessagePage;
import com.pydlove.workcove.mail.domain.model.MailOutgoingMessage;
import com.pydlove.workcove.mail.domain.model.MailSyncMessage;

import java.util.List;

public interface MailGateway {

    void verify(MailConnectionSettings settings);

    List<MailFolder> folders(MailConnectionSettings settings);

    MailFolder createFolder(
            MailConnectionSettings settings,
            String name);

    MailMessagePage messages(
            MailConnectionSettings settings,
            String folderPath,
            boolean flagged,
            int limit,
            int offset);

    List<MailSyncMessage> syncMessages(
            MailConnectionSettings settings,
            String folderPath);

    MailMessageDetail message(
            MailConnectionSettings settings,
            String folderPath,
            String uid);

    void reply(
            MailConnectionSettings settings,
            MailMessageDetail source,
            String body);

    void send(
            MailConnectionSettings settings,
            MailOutgoingMessage message);

    void setStarred(
            MailConnectionSettings settings,
            String folderPath,
            String uid,
            boolean starred);

    void setSeen(
            MailConnectionSettings settings,
            String folderPath,
            String uid,
            boolean seen);

    void markAllSeen(
            MailConnectionSettings settings,
            String folderPath);

    void move(
            MailConnectionSettings settings,
            String folderPath,
            String uid,
            String targetFolderPath);

    MailExportedMessage export(
            MailConnectionSettings settings,
            String folderPath,
            String uid);
}
