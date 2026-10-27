package com.paytech.client.master.domainservice.service.impl;

import java.time.Instant;

import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.client.master.core.dal.dataobject.ApplicationDeclaration;
import com.paytech.client.master.core.dal.dataobject.ClientApplicationSubmission;
import com.paytech.client.master.domainservice.repository.ApplicationDeclarationRepository;
import com.paytech.client.master.domainservice.repository.ClientApplicationRepository;
import com.paytech.client.master.domainservice.service.ClientApplicationCommandService;
import com.paytech.client.master.service.handler.ClientReviewWithdrawalHandler;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;

import lombok.AllArgsConstructor;

/** 声明与撤回的本地短事务。 */
@AllArgsConstructor
public class ClientApplicationCommandServiceImpl implements ClientApplicationCommandService {
    private final TransactionTemplate writes;
    private final CompensationEventRegistrar events;
    private final ClientApplicationRepository applications;
    private final ApplicationDeclarationRepository declarations;

    /** 追加声明。 */
    @Override
    public void declare(ApplicationDeclaration declaration) {
        writes.executeWithoutResult(status -> declarations.insert(declaration));
    }

    /** 条件撤回；有审核任务时同一事务登记撤回通知任务。 */
    @Override
    public boolean withdraw(ClientApplicationSubmission application, Instant now) {
        return Boolean.TRUE.equals(writes.execute(status -> {
            if (!applications.withdraw(application, now)) {
                return false;
            }
            if (application.getDueDiligenceCaseId() != null) {
                events.register(ClientReviewWithdrawalHandler.buildEvent(application.getApplicationId()));
            }
            return true;
        }));
    }
}
