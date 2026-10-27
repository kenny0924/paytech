package com.paytech.client.master.domainservice.service.impl;

import java.time.Instant;

import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.client.master.core.dal.dataobject.PartyIdentityKey;
import com.paytech.client.master.domain.onboarding.ClientSubmissionSubject;
import com.paytech.client.master.domain.onboarding.SubmitClientApplicationContext;
import com.paytech.client.master.domainservice.repository.ClientApplicationRepository;
import com.paytech.client.master.domainservice.repository.PartyIdentityKeyRepository;
import com.paytech.client.master.domainservice.repository.PartyProfileIdentifierRepository;
import com.paytech.client.master.domainservice.service.ClientSubmissionService;
import com.paytech.client.master.service.handler.ClientReviewRegistrationHandler;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.foundation.id.IdGenerator;

import lombok.AllArgsConstructor;

/** 提交状态、身份绑定与审核登记任务的本地原子保存。 */
@AllArgsConstructor
public class ClientSubmissionServiceImpl implements ClientSubmissionService {
    private final IdGenerator ids;
    private final TransactionTemplate transactions;
    private final CompensationEventRegistrar events;
    private final PartyIdentityKeyRepository identityKeys;
    private final ClientApplicationRepository applications;
    private final PartyProfileIdentifierRepository identifiers;

    /** 一个本地事务完成状态迁移、身份绑定和任务登记。 */
    @Override
    public void submit(SubmitClientApplicationContext context) {
        transactions.executeWithoutResult(status -> {
            applications.submit(context.getApplication(), context.getSubmissionId(), context.getNow());
            for (ClientSubmissionSubject subject : context.getSubjects()) {
                if (subject.needsBinding()) {
                    bindIdentity(subject, context.getNow());
                }
            }
            events.register(ClientReviewRegistrationHandler.buildEvent(context.getSubmissionId()));
        });
    }

    /** 复用同一主体已有身份键，否则以 Token 或公开登记号新建；其他主体已占用时由唯一索引 uk_party_identity_token 拒绝。 */
    void bindIdentity(ClientSubmissionSubject subject, Instant now) {
        PartyIdentityKey key = subject.getExistingKey();
        if (key == null) {
            key = new PartyIdentityKey();
            key.setIdentityKeyId(ids.nextId());
            key.setPartyId(subject.getPartyId());
            key.setIdentifierType(subject.getFields().identifierIdentifierType());
            key.setIssuingCountry(subject.getIssuerScopeKey().equals(subject.getFields().identifierIssuingCountry())
                ? subject.getIssuerScopeKey() : null);
            key.setIssuerScopeKey(subject.getIssuerScopeKey());
            key.setIdentifierKeyValue(subject.identifierKeyValue());
            key.setCreateTime(now);
            key.setUpdateTime(now);
            identityKeys.insert(key);
        }
        identifiers.bind(subject.getIdentifier(), key.getIdentityKeyId(), subject.getIssuerScopeKey(), now);
    }
}
