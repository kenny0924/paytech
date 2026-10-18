package com.paytech.client.master.domainservice.service.impl;

import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.client.master.domain.onboarding.ClientReviewOutcomeContext;
import com.paytech.client.master.domainservice.repository.ClientApplicationRepository;
import com.paytech.client.master.domainservice.repository.ClientProjectionRepository;
import com.paytech.client.master.domainservice.repository.ClientRepository;
import com.paytech.client.master.domainservice.repository.PartyProfileRepository;
import com.paytech.client.master.domainservice.service.ClientReviewOutcomeService;
import com.paytech.client.master.service.handler.ClientTenantProjectionHandler;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;

import lombok.AllArgsConstructor;

/** 审核结果的本地原子落地；Platform 工作区投影在提交后由既有补偿任务完成。 */
@AllArgsConstructor
public class ClientReviewOutcomeServiceImpl implements ClientReviewOutcomeService {
    private final ClientRepository clients;
    private final TransactionTemplate writes;
    private final CompensationEventRegistrar events;
    private final ClientProjectionRepository projections;
    private final ClientApplicationRepository applications;
    private final PartyProfileRepository profiles;

    /** 申请结论、Client 激活、主体资料批准时间、生命周期历史与投影任务同一事务提交。 */
    @Override
    public void conclude(ClientReviewOutcomeContext context) {
        writes.executeWithoutResult(status -> {
            applications.concludeReview(context.getApplication(), context.getStatus(), context.getDecisionId(), context.getNow());
            if (!context.activates()) {
                return;
            }
            clients.activate(context.getApplication().getClientId(), context.getNow());
            profiles.markApproved(context.getActivation().partyId(), context.getNow());
            projections.append(context.getActivation(), context.getActivationSnapshot());
            events.register(ClientTenantProjectionHandler.buildEvent(context.getActivation().changeVersionId()));
        });
    }
}
