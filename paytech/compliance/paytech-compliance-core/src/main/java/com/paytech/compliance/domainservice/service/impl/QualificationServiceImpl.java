package com.paytech.compliance.domainservice.service.impl;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.compliance.domain.QualificationChangeContext;
import com.paytech.compliance.domainservice.repository.QualificationDecisionRepository;
import com.paytech.compliance.domainservice.repository.QualificationStateChangeRepository;
import com.paytech.compliance.domainservice.repository.RoleQualificationRepository;
import com.paytech.compliance.domainservice.service.QualificationService;

import lombok.AllArgsConstructor;

/** 角色资格变更的本地原子保存。 */
@Service
@AllArgsConstructor
public class QualificationServiceImpl implements QualificationService {
    private final PlatformTransactionManager transactionManager;
    private final QualificationDecisionRepository decisions;
    private final RoleQualificationRepository qualifications;
    private final QualificationStateChangeRepository stateChanges;

    /** 一个 READ COMMITTED、5 秒超时的短事务保存决定、投影与状态变更。 */
    @Override
    public void saveChange(QualificationChangeContext context) {
        transaction().executeWithoutResult(status -> {
            decisions.insert(context.getDecision());
            saveProjection(context);
            stateChanges.insert(context.getStateChange());
        });
    }

    /** 没有投影时新建，否则按读取时版本条件切换；外层事务负责提交。 */
    void saveProjection(QualificationChangeContext context) {
        if (context.getCurrent() == null) {
            qualifications.insert(context.getCreated());
            return;
        }
        Instant now = context.getStateChange().getOccurredTime();
        qualifications.applyDecision(context.getCurrent(), context.getDecision(), now);
    }

    /** READ COMMITTED、5 秒超时的本地短事务。 */
    TransactionTemplate transaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(5);
        return transaction;
    }
}
