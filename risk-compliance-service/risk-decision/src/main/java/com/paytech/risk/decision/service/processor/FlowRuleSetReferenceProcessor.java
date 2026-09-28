package com.paytech.risk.decision.service.processor;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.risk.decision.core.dal.dataobject.DecisionFlowRow;
import com.paytech.risk.decision.core.dal.dataobject.DecisionFlowVersionRow;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPoint;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPointVersion;
import com.paytech.risk.decision.core.dal.dataobject.RuleSet;
import com.paytech.risk.decision.core.dal.dataobject.RuleSetVersion;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Node;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Reference;
import com.paytech.risk.decision.domain.flow.DecisionFlowGraphAssembler;
import com.paytech.risk.decision.domain.query.FlowRuleSetReferenceView;
import com.paytech.risk.decision.domain.query.LegacyPointFlowContext;
import com.paytech.risk.decision.domain.query.LegacyPointFlowView;
import com.paytech.risk.decision.domain.variable.VariableRuleConfiguration;
import com.paytech.risk.decision.domainservice.repository.DecisionFlowEdgeRepository;
import com.paytech.risk.decision.domainservice.repository.DecisionFlowNodeRepository;
import com.paytech.risk.decision.domainservice.repository.DecisionFlowRootRepository;
import com.paytech.risk.decision.domainservice.repository.DecisionFlowVersionRepository;
import com.paytech.risk.decision.domainservice.repository.DecisionPointRuleSetRepository;
import com.paytech.risk.decision.domainservice.repository.DecisionPointVersionRepository;
import com.paytech.risk.decision.domainservice.repository.DecisionRuleSetRepository;
import com.paytech.risk.decision.domainservice.service.DecisionConfigurationNotFoundException;
import com.paytech.risk.decision.domainservice.service.DecisionFlowReferenceService;
import com.paytech.risk.decision.domainservice.service.DecisionRuleSetService;
import com.paytech.risk.decision.domainservice.service.RiskVariableService;
import com.paytech.risk.decision.service.processor.request.FlowRuleSetReferenceRequest;

import lombok.AllArgsConstructor;

/** 在 Owner 模块内组合只读事实，保持同一 REPEATABLE_READ 快照。 */
@Component
@AllArgsConstructor
public class FlowRuleSetReferenceProcessor extends AbstractProcessor<FlowRuleSetReferenceRequest, FlowRuleSetReferenceView> {
    private final Clock clock;
    private final RiskVariableService variables;
    private final DecisionRuleSetService ruleService;
    private final DecisionRuleSetRepository rules;
    private final DecisionFlowEdgeRepository edges;
    private final DecisionFlowGraphAssembler assembler;
    private final DecisionFlowNodeRepository nodes;
    private final DecisionFlowRootRepository roots;
    private final PlatformTransactionManager transactionManager;
    private final DecisionFlowReferenceService references;
    private final DecisionFlowVersionRepository versions;
    private final DecisionPointRuleSetRepository bindings;
    private final DecisionPointVersionRepository pointVersions;

    /** 公共模板校验输入后进入本地只读快照，不获取资源锁或写锁。 */
    @Override
    protected FlowRuleSetReferenceView processRequest(FlowRuleSetReferenceRequest request) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return transaction.execute(status -> read(request));
    }

    /** 组合该查询需要的真实记录与目录，不改变业务事实。 */
    private FlowRuleSetReferenceView read(FlowRuleSetReferenceRequest request) {
        long ruleVersionId;
        if ("FLOW_VERSION".equals(request.getSourceType())) {
            DecisionFlowVersionRow version = versions.findById(request.getSourceVersionId())
                .orElseThrow(DecisionConfigurationNotFoundException::new);
            DecisionFlowRow root = roots.findById(version.flowId()).orElseThrow(DecisionConfigurationNotFoundException::new);
            DecisionPoint point = rules.findPoint(root.decisionPointId())
                .orElseThrow(() -> new ErrorException("流程持久化配置不完整或身份不一致"));
            var graph = assembler.assemble(point.pointCode(), root, version,
                nodes.findByVersion(version.flowVersionId()), edges.findByVersion(version.flowVersionId()));
            Node node = graph.nodes().stream().filter(n -> n.nodeKey().equals(request.getNodeKey()) && "RULE_SET".equals(n.type()))
                .findFirst().orElseThrow(DecisionConfigurationNotFoundException::new);
            ruleVersionId = node.ruleSetVersionId();
            references.describe(node.nodeKey(), ruleVersionId, root.decisionPointId(), version.inputContractCode(), version.inputContractVersion());
        } else {
            LegacyPointFlowView legacy = legacyPoint(request.getSourceVersionId());
            ruleVersionId = legacy.getRuleSetReferences().stream().filter(r -> r.nodeKey().equals(request.getNodeKey()))
                .findFirst().orElseThrow(DecisionConfigurationNotFoundException::new).ruleSetVersionId();
        }
        RuleSetVersion version = rules.findVersion(ruleVersionId)
            .orElseThrow(() -> new ErrorException("流程持久化配置不完整或身份不一致"));
        RuleSet root = rules.findRuleSet(version.decisionPointId(), version.ruleSetCode())
            .orElseThrow(() -> new ErrorException("流程持久化配置不完整或身份不一致"));
        var ruleSet = ruleService.detailForPersistedReference(ruleVersionId);
        var catalog = variables.currentValues(VariableRuleConfiguration.references(ruleSet), clock.instant().truncatedTo(ChronoUnit.MICROS));
        return new FlowRuleSetReferenceView(request.getSourceType(), request.getSourceVersionId(), request.getNodeKey(),
            root.getStatus().getCode(), ruleSet, catalog);
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
