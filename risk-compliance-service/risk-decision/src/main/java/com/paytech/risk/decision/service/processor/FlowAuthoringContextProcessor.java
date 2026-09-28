package com.paytech.risk.decision.service.processor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPoint;
import com.paytech.risk.decision.domain.enums.DecisionPointStatus;
import com.paytech.risk.decision.domain.query.FlowAuthoringContextView;
import com.paytech.risk.decision.domainservice.repository.DecisionRuleSetRepository;
import com.paytech.risk.decision.domainservice.service.DecisionConfigurationNotFoundException;
import com.paytech.risk.decision.domainservice.service.RiskVariableService;
import com.paytech.risk.decision.service.processor.request.FlowAuthoringContextRequest;

import lombok.AllArgsConstructor;

/** 在 Owner 模块内组合只读事实，保持同一 REPEATABLE_READ 快照。 */
@Component
@AllArgsConstructor
public class FlowAuthoringContextProcessor
    extends AbstractProcessor<FlowAuthoringContextRequest, FlowAuthoringContextView> {
    private final RiskVariableService variables;
    private final DecisionRuleSetRepository rules;
    private final PlatformTransactionManager transactionManager;

    /** 公共模板校验输入后进入本地只读快照，不获取资源锁或写锁。 */
    @Override
    protected FlowAuthoringContextView processRequest(FlowAuthoringContextRequest request) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return transaction.execute(status -> read(request));
    }

    /** 组合该查询需要的真实记录与目录，不改变业务事实。 */
    private FlowAuthoringContextView read(FlowAuthoringContextRequest request) {
        DecisionPoint point = point(request.getPointCode() == null ? "CHECKOUT_PREPARE" : request.getPointCode());
        return FlowAuthoringContextView.from(variables.systemDefinitions(point.pointCode()));
    }

    /** 可配置决策点必须是本期支持且当前有效的目录项。 */
    private DecisionPoint point(String code) {
        if (!"CHECKOUT_PREPARE".equals(code)) {
            throw new DecisionConfigurationNotFoundException();
        }
        DecisionPoint point = rules.findPointByCode(code).orElseThrow(DecisionConfigurationNotFoundException::new);
        if (point.status() != DecisionPointStatus.ACTIVE) {
            throw new InvalidParameterException("决策点当前不可配置");
        }
        return point;
    }
}
