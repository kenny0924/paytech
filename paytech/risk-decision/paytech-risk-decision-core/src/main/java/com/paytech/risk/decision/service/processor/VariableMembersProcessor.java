package com.paytech.risk.decision.service.processor;

import static com.paytech.risk.decision.domainservice.policy.VariableQueryPolicy.pattern;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.risk.decision.domain.variable.RiskVariable.MemberPage;
import com.paytech.risk.decision.domainservice.query.RiskVariableReader;
import com.paytech.risk.decision.service.processor.request.VariableMembersRequest;

import lombok.AllArgsConstructor;

/**
 * 分页读取集合变量的成员
 * <li>根版本校验、成员页与总数在同一只读可重复读快照内读取；后续页携带首次根版本，版本变化返回冲突，防止翻页混合不同批次。</li>
 */
@Component
@AllArgsConstructor
public class VariableMembersProcessor extends AbstractProcessor<VariableMembersRequest, MemberPage> {
    private final Clock clock;
    private final RiskVariableReader reader;
    private final PlatformTransactionManager transactionManager;

    @Override
    protected MemberPage processRequest(VariableMembersRequest request) {
        String keywordPattern = pattern(request.getKeyword());
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return transaction
            .execute(status -> reader.members(request.getVariableCode(), keywordPattern, request.getPage(),
                request.getSize(), request.getExpectedVersion(), clock.instant().truncatedTo(ChronoUnit.MICROS)));
    }
}
