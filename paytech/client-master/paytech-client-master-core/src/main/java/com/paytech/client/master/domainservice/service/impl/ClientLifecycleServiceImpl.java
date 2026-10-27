package com.paytech.client.master.domainservice.service.impl;

import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.client.master.domain.onboarding.ClientSuspensionContext;
import com.paytech.client.master.domainservice.repository.ClientProjectionRepository;
import com.paytech.client.master.domainservice.repository.ClientRepository;
import com.paytech.client.master.domainservice.service.ClientLifecycleService;
import com.paytech.client.master.service.handler.ClientTenantProjectionHandler;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;

import lombok.AllArgsConstructor;

/** 挂起与历史、投影任务同一短事务；状态或历史根已变化时整体回滚，由消息重投处理。 */
@AllArgsConstructor
public class ClientLifecycleServiceImpl implements ClientLifecycleService {
    private final ClientRepository clients;
    private final TransactionTemplate writes;
    private final CompensationEventRegistrar events;
    private final ClientProjectionRepository projections;

    /** 同一事务挂起、追加历史并登记投影任务。 */
    @Override
    public void suspend(ClientSuspensionContext context) {
        writes.executeWithoutResult(status -> {
            clients.suspend(context.getClientId(), context.getNow());
            projections.append(context.getHistory(), context.getSnapshot());
            events.register(ClientTenantProjectionHandler.buildEvent(context.getHistory().changeVersionId()));
        });
    }
}
