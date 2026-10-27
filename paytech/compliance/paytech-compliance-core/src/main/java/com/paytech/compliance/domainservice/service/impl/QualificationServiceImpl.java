package com.paytech.compliance.domainservice.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.compliance.domain.QualificationChangeContext;
import com.paytech.compliance.domainservice.repository.QualificationDecisionRepository;
import com.paytech.compliance.domainservice.repository.QualificationStateChangeRepository;
import com.paytech.compliance.domainservice.repository.RoleQualificationRepository;
import com.paytech.compliance.domainservice.service.QualificationService;
import com.paytech.compliance.service.handler.QualificationChangedHandler;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;

import lombok.AllArgsConstructor;

/** 角色资格变更的本地原子保存。 */
@Service
@AllArgsConstructor
public class QualificationServiceImpl implements QualificationService {
    private final CompensationEventRegistrar events;
    private final PlatformTransactionManager transactionManager;
    private final QualificationDecisionRepository decisions;
    private final RoleQualificationRepository qualifications;
    private final QualificationStateChangeRepository stateChanges;

    /** 一个 READ COMMITTED、5 秒超时的短事务保存决定、投影、状态变更并登记 QualificationChanged 发布。 */
    @Override
    public void saveChange(QualificationChangeContext context) {
        transaction().executeWithoutResult(status -> {
            decisions.insert(context.getDecision());
            applyProjection(context);
        });
    }

    /**
     * 投影切换、状态变更与发布任务登记；必须在调用方事务内执行
     * <li>案件决定把决定随案件一起插入后调用本方法，手工登记 / 撤销由 {@link #saveChange} 调用。</li>
     */
    @Override
    public void applyProjection(QualificationChangeContext context) {
        if (context.getCurrent() == null) {
            qualifications.insert(context.getCreated());
        } else {
            qualifications.applyDecision(context.getCurrent(), context.getTarget(), context.getStateChange().getOccurredTime());
        }
        stateChanges.insert(context.getStateChange());
        events.register(QualificationChangedHandler.buildEvent(context.getStateChange().getQualificationId(),
            context.getStateChange().getAggregateVersion()));
    }

    /** READ COMMITTED、5 秒超时的本地短事务。 */
    TransactionTemplate transaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(5);
        return transaction;
    }
}
