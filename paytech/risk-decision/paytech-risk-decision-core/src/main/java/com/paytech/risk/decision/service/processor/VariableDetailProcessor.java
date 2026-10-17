package com.paytech.risk.decision.service.processor;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.risk.decision.domain.variable.VariableDetailResponse;
import com.paytech.risk.decision.domainservice.query.RiskVariableReader;
import com.paytech.risk.decision.service.processor.request.VariableDetailRequest;

import lombok.AllArgsConstructor;

/**
 * 按永久编码读取变量当前详情，包括停用与墓碑
 * <li>根、成员预览与可选值集合预览在同一只读可重复读快照内读取。</li>
 */
@Component
@AllArgsConstructor
public class VariableDetailProcessor extends AbstractProcessor<VariableDetailRequest, VariableDetailResponse> {
    private final Clock clock;
    private final RiskVariableReader reader;
    private final PlatformTransactionManager transactionManager;

    @Override
    protected VariableDetailResponse processRequest(VariableDetailRequest request) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return transaction.execute(status -> new VariableDetailResponse(reader.detail(request.getVariableCode()),
            clock.instant().truncatedTo(ChronoUnit.MICROS)));
    }
}
