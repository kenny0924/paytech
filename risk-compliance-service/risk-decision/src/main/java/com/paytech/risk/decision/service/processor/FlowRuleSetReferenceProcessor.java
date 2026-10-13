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
import com.paytech.risk.decision.core.dal.dataobject.DecisionPointRuleSet;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPointVersion;
import com.paytech.risk.decision.core.dal.dataobject.RuleSet;
import com.paytech.risk.decision.core.dal.dataobject.RuleSetVersion;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Node;
import com.paytech.risk.decision.domain.flow.DecisionFlowGraphAssembler;
import com.paytech.risk.decision.domain.query.FlowRuleSetReferenceView;
import com.paytech.risk.decision.domain.query.LegacyPointFlowContext;
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
        Target target = "FLOW_VERSION".equals(request.getSourceType()) ? flowTarget(request) : legacyTarget(request);
        long ruleVersionId = target.ruleSetVersionId();
        RuleSetVersion version = rules.findVersion(ruleVersionId)
            .orElseThrow(() -> new ErrorException("流程持久化配置不完整或身份不一致"));
        RuleSet root = rules.findRuleSet(version.decisionPointId(), version.ruleSetCode())
            .orElseThrow(() -> new ErrorException("流程持久化配置不完整或身份不一致"));
        var ruleSet = target.orchestrationBindingId() == null ? ruleService.detailForPersistedReference(ruleVersionId)
            : ruleService.detailForLegacyOrchestration(ruleVersionId, target.orchestrationBindingId());
        var catalog = variables.currentValues(VariableRuleConfiguration.references(ruleSet), clock.instant().truncatedTo(ChronoUnit.MICROS));
        return new FlowRuleSetReferenceView(request.getSourceType(), request.getSourceVersionId(), request.getNodeKey(),
            root.getStatus().getCode(), ruleSet, catalog);
    }

    /** 流程版本图中已保存的规则集节点。 */
    private Target flowTarget(FlowRuleSetReferenceRequest request) {
        DecisionFlowVersionRow version = versions.findById(request.getSourceVersionId())
            .orElseThrow(DecisionConfigurationNotFoundException::new);
        DecisionFlowRow root = roots.findById(version.flowId()).orElseThrow(DecisionConfigurationNotFoundException::new);
        DecisionPoint point = rules.findPoint(root.decisionPointId())
            .orElseThrow(() -> new ErrorException("流程持久化配置不完整或身份不一致"));
        var graph = assembler.assemble(point.pointCode(), root, version,
            nodes.findByVersion(version.flowVersionId()), edges.findByVersion(version.flowVersionId()));
        Node node = graph.nodes().stream().filter(n -> n.nodeKey().equals(request.getNodeKey()) && "RULE_SET".equals(n.type()))
            .findFirst().orElseThrow(DecisionConfigurationNotFoundException::new);
        references.describe(node.nodeKey(), node.ruleSetVersionId(), root.decisionPointId(), version.inputContractCode(), version.inputContractVersion());
        return new Target(node.ruleSetVersionId(), null);
    }

    /** 旧点版本的准确绑定；多绑定旧点的绑定只是顺序编排，不作为规则版本的 Companion。 */
    private Target legacyTarget(FlowRuleSetReferenceRequest request) {
        List<DecisionPointRuleSet> legacyBindings = legacyBindings(request.getSourceVersionId());
        DecisionPointRuleSet binding = legacyBindings.stream().filter(b -> nodeKey(b).equals(request.getNodeKey()))
            .findFirst().orElseThrow(DecisionConfigurationNotFoundException::new);
        return new Target(binding.ruleSetVersionId(), legacyBindings.size() > 1 ? binding.bindingId() : null);
    }

    /** 读取完整旧绑定并核验顺序，再核验全部准确引用；不能只信任请求节点。 */
    private List<DecisionPointRuleSet> legacyBindings(long id) {
        DecisionPointVersion version = pointVersions.findById(id).orElseThrow(DecisionConfigurationNotFoundException::new);
        DecisionPoint point = rules.findPoint(version.decisionPointId())
            .orElseThrow(() -> new ErrorException("流程持久化配置不完整或身份不一致"));
        var context = new LegacyPointFlowContext(point, version, bindings.findLegacyBindings(id));
        context.verifyBindings();
        context.getBindings().forEach(binding -> references.describe(nodeKey(binding), binding.ruleSetVersionId(),
            point.decisionPointId(), version.inputContractCode(), version.inputContractVersion()));
        return context.getBindings();
    }

    /** 旧绑定在只读图中的节点键，与旧点投影保持一致。 */
    private static String nodeKey(DecisionPointRuleSet binding) {
        return "binding_" + binding.bindingId();
    }

    /** 被引用的准确规则版本；仅旧多绑定点携带需排除 Companion 判断的绑定。 */
    private record Target(long ruleSetVersionId, Long orchestrationBindingId) {
    }
}
