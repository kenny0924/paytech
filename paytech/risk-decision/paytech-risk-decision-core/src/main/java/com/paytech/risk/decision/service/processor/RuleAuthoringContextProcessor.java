package com.paytech.risk.decision.service.processor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.risk.decision.domain.authoring.DecisionPointAuthoringDescriptor;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAssembler;
import com.paytech.risk.decision.domainservice.repository.DecisionRuleSetRepository;
import com.paytech.risk.decision.domainservice.service.DecisionConfigurationNotFoundException;
import com.paytech.risk.decision.domainservice.service.DecisionRuleSetService.RuleAuthoringView;
import com.paytech.risk.decision.domainservice.service.RiskVariableService;
import com.paytech.risk.decision.service.processor.request.RuleAuthoringContextRequest;
import com.paytech.risk.decision.service.query.RuleSetConfigurationReader;

import lombok.AllArgsConstructor;

/** 在同一快照中验证配置目录并读取当前系统变量。 */
@Component
@AllArgsConstructor
public class RuleAuthoringContextProcessor extends AbstractProcessor<RuleAuthoringContextRequest, RuleAuthoringView> {
    private final RiskVariableService variableService;
    private final DecisionRuleSetAssembler assembler;
    private final DecisionRuleSetRepository repository;
    private final PlatformTransactionManager transactionManager;
    private final RuleSetConfigurationReader reader;

    /** 工程目录与数据库动作必须一致才返回可配置能力。 */
    @Override
    protected RuleAuthoringView processRequest(RuleAuthoringContextRequest request) {
        DecisionPointAuthoringDescriptor descriptor = DecisionPointAuthoringDescriptor.find(request.getPointCode())
            .orElseThrow(DecisionConfigurationNotFoundException::new);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return transaction.execute(status -> {
            var point = repository.findPointByCode(request.getPointCode())
                .orElseThrow(DecisionConfigurationNotFoundException::new);
            assembler.verifyPoint(point);
            reader.requireCatalog(point.pointCode());
            return RuleAuthoringView.from(descriptor, variableService.systemDefinitions(request.getPointCode()));
        });
    }
}
