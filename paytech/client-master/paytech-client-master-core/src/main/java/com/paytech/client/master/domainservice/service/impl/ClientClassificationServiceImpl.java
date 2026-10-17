package com.paytech.client.master.domainservice.service.impl;

import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.client.master.domain.onboarding.ClientClassificationContext;
import com.paytech.client.master.domainservice.repository.ClientBusinessClassificationRepository;
import com.paytech.client.master.domainservice.repository.ClientBusinessProfileRepository;
import com.paytech.client.master.domainservice.service.ClientClassificationService;
import com.paytech.client.master.service.handler.ClientClassificationHandler;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;

import lombok.AllArgsConstructor;

/** 分类的本地原子保存；确认与交付任务登记同一事务提交。 */
@AllArgsConstructor
public class ClientClassificationServiceImpl implements ClientClassificationService {
    private final TransactionTemplate writes;
    private final CompensationEventRegistrar events;
    private final ClientBusinessProfileRepository profiles;
    private final ClientBusinessClassificationRepository classifications;

    /** 先条件绑定任务再整体替换行；绑定条件保证本任务确认后不能再改。 */
    @Override
    public void replace(ClientClassificationContext context) {
        writes.executeWithoutResult(status -> {
            profiles.bindClassificationCase(context.getProfile(), context.getCaseId(), context.getNow());
            classifications.deleteByProfile(context.getProfile().getBusinessProfileId());
            context.getClassifications().forEach(classifications::insert);
        });
    }

    /** 条件确认后登记 I02 交付任务。 */
    @Override
    public void confirm(ClientClassificationContext context) {
        writes.executeWithoutResult(status -> {
            profiles.confirmClassification(context.getProfile(), context.getActorId(), context.getNow());
            events.register(ClientClassificationHandler.buildEvent(context.getCaseId()));
        });
    }
}
