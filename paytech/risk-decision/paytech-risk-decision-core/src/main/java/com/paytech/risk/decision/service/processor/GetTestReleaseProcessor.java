package com.paytech.risk.decision.service.processor;

import java.util.NoSuchElementException;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.risk.decision.domain.release.DecisionReleaseAggregate;
import com.paytech.risk.decision.domain.release.DecisionReleaseAssembler;
import com.paytech.risk.decision.domainservice.repository.DecisionReleaseRepository;
import com.paytech.risk.decision.domainservice.service.DecisionConfigurationNotFoundException;
import com.paytech.risk.decision.domainservice.service.DecisionRuleSetService;
import com.paytech.risk.decision.service.processor.request.GetTestReleaseRequest;
import com.paytech.risk.decision.service.query.DecisionReleaseReader;

import lombok.AllArgsConstructor;

/** TEST Candidate 只读查询的 Owner 编排。 */
@Component
@AllArgsConstructor
public class GetTestReleaseProcessor extends AbstractProcessor<GetTestReleaseRequest, DecisionReleaseAggregate> {
    private final DecisionReleaseReader reader;
    private final DecisionRuleSetService ruleSetService;
    private final DecisionReleaseAssembler assembler;
    private final DecisionReleaseRepository releases;
    private final PlatformTransactionManager transactionManager;

    /** 查询只持有本地只读快照，不获取资源锁或写锁。 */
    @Override
    protected DecisionReleaseAggregate processRequest(GetTestReleaseRequest request) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return transaction.execute(status -> {
            var root =
                releases.findRoot(request.getReleaseId()).orElseThrow(DecisionConfigurationNotFoundException::new);
            try {
                var stored = reader.requireClosure(root);
                var rule = ruleSetService
                    .detailForPersistedReference(stored.frozenComposition().ruleSetVersion().ruleSetVersionId());
                return assembler.domainAggregate(stored, rule.ruleSetProjection());
            } catch (NoSuchElementException missing) {
                throw new ErrorException("首次命中后 Release 闭包消失", missing);
            }
        });
    }
}
