package com.paytech.risk.decision.service.processor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.risk.decision.domain.variable.VariableReferences;
import com.paytech.risk.decision.domainservice.query.RiskVariableReader;
import com.paytech.risk.decision.domainservice.service.RiskVariableReferenceService;
import com.paytech.risk.decision.service.processor.request.VariableReferencesRequest;

import lombok.AllArgsConstructor;

/**
 * 分段扫描变量的当前与历史引用位置
 * <li>根存在性、本页初始上界及当前/历史判定在同一只读可重复读快照内读取；跨页不承诺快照。</li>
 */
@Component
@AllArgsConstructor
public class VariableReferencesProcessor extends AbstractProcessor<VariableReferencesRequest, VariableReferences.Page> {
    private final RiskVariableReader reader;
    private final RiskVariableReferenceService references;
    private final PlatformTransactionManager transactionManager;

    @Override
    protected VariableReferences.Page processRequest(VariableReferencesRequest request) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return transaction.execute(status -> {
            reader.requiredRoot(request.getVariableCode(), null);
            return references.page(request.getVariableCode(), request.getScope(), request.getCursor(), request.getSize());
        });
    }
}
