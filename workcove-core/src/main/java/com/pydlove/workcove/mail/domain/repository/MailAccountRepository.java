package com.pydlove.workcove.mail.domain.repository;

import com.pydlove.workcove.mail.domain.model.MailAccount;

import java.util.List;
import java.util.Optional;

public interface MailAccountRepository {

    List<MailAccount> findAccounts(long ownerId, String workspaceId);

    Optional<MailAccount> findAccount(
            long ownerId,
            String workspaceId,
            long accountId);

    Optional<MailAccount> findAccountByAddress(
            long ownerId,
            String workspaceId,
            String emailAddress);

    MailAccount save(MailAccount account);

    void delete(long ownerId, String workspaceId, long accountId);
}
