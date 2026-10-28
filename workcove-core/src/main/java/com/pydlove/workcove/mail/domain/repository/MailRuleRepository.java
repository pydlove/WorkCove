package com.pydlove.workcove.mail.domain.repository;

import com.pydlove.workcove.mail.domain.model.MailRule;

import java.util.List;
import java.util.Optional;

public interface MailRuleRepository {

    List<MailRule> findRules(
            long ownerId,
            String workspaceId,
            long accountId);

    Optional<MailRule> findRule(
            long ownerId,
            String workspaceId,
            long accountId,
            long ruleId);

    MailRule save(MailRule rule);

    void delete(
            long ownerId,
            String workspaceId,
            long accountId,
            long ruleId);
}
