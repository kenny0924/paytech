package com.paytech.risk.decision.service.processor;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate;
import com.paytech.risk.decision.domain.variable.RiskVariable;
import com.paytech.risk.decision.domain.variable.VariableRuleConfiguration;
import com.paytech.risk.decision.domainservice.service.DecisionRuleSetService;
import com.paytech.risk.decision.domainservice.service.RiskVariableService;
import com.paytech.risk.decision.service.processor.request.RuleSetDetailRequest;

import lombok.AllArgsConstructor;

/** 不可变规则详情与当前共享变量属于同一查询快照。 */
@Component
@AllArgsConstructor
public class RuleSetDetailProcessor
    extends AbstractProcessor<RuleSetDetailRequest, RuleSetDetailProcessor.RuleSetRead> {
    private final Clock clock;
    private final RiskVariableService variableService;
    private final DecisionRuleSetService ruleSetService;
    private final PlatformTransactionManager transactionManager;

    /** 历史 Hash 仅核验原规则内容，当前变量只作为展示事实。 */
    @Override
    protected RuleSetRead processRequest(RuleSetDetailRequest request) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return transaction.execute(status -> {
            var aggregate = ruleSetService.detail(request.getRuleSetVersionId());
            return new RuleSetRead(aggregate, variableService.currentValues(
                VariableRuleConfiguration.references(aggregate), clock.instant().truncatedTo(ChronoUnit.MICROS)));
        });
    }

    /** 查询结果，不作为事务写入 Context。 */
    public record RuleSetRead(DecisionRuleSetAggregate aggregate, RiskVariable.Catalog catalog) {
    }
}
