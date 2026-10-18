package com.paytech.risk.decision.service.processor;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPoint;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPointVersion;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Reference;
import com.paytech.risk.decision.domain.query.LegacyPointFlowContext;
import com.paytech.risk.decision.domain.query.LegacyPointFlowView;
import com.paytech.risk.decision.domainservice.repository.DecisionPointRuleSetRepository;
import com.paytech.risk.decision.domainservice.repository.DecisionPointVersionRepository;
import com.paytech.risk.decision.domainservice.repository.DecisionRuleSetRepository;
import com.paytech.risk.decision.domainservice.service.DecisionConfigurationNotFoundException;
import com.paytech.risk.decision.domainservice.service.DecisionFlowReferenceService;
import com.paytech.risk.decision.service.processor.request.LegacyPointFlowRequest;

import lombok.AllArgsConstructor;

/** 在 Owner 模块内组合只读事实，保持同一 REPEATABLE_READ 快照。 */
@Component
@AllArgsConstructor
public class LegacyPointFlowProcessor extends AbstractProcessor<LegacyPointFlowRequest, LegacyPointFlowView> {
    private final DecisionRuleSetRepository rules;
    private final PlatformTransactionManager transactionManager;
    private final DecisionFlowReferenceService references;
    private final DecisionPointRuleSetRepository bindings;
    private final DecisionPointVersionRepository pointVersions;

    /** 公共模板校验输入后进入本地只读快照，不获取资源锁或写锁。 */
    @Override
    protected LegacyPointFlowView processRequest(LegacyPointFlowRequest request) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return transaction.execute(status -> read(request));
    }

    /** 组合该查询需要的真实记录与目录，不改变业务事实。 */
    private LegacyPointFlowView read(LegacyPointFlowRequest request) {
        return legacyPoint(request.getPointVersionId());
    }

    /** 读取完整旧绑定并核验顺序，再解析全部准确引用；不能只信任请求节点。 */
    private LegacyPointFlowView legacyPoint(long id) {
        DecisionPointVersion version = pointVersions.findById(id).orElseThrow(DecisionConfigurationNotFoundException::new);
        DecisionPoint point = rules.findPoint(version.decisionPointId())
            .orElseThrow(() -> new ErrorException("流程持久化配置不完整或身份不一致"));
        var context = new LegacyPointFlowContext(point, version, bindings.findLegacyBindings(id));
        context.verifyBindings();
        List<Reference> refs = context.getBindings().stream().map(binding -> references.describe("binding_" + binding.bindingId(),
            binding.ruleSetVersionId(), point.decisionPointId(), version.inputContractCode(), version.inputContractVersion())).toList();
        return context.toView(refs);
    }
}
