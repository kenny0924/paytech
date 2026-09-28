package com.paytech.risk.decision.service.processor;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.risk.decision.core.dal.dataobject.DecisionFlowRow;
import com.paytech.risk.decision.core.dal.dataobject.DecisionFlowVersionRow;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPoint;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Node;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Reference;
import com.paytech.risk.decision.domain.flow.DecisionFlowGraphAssembler;
import com.paytech.risk.decision.domain.query.FlowDetailView;
import com.paytech.risk.decision.domain.variable.VariableFlowConfiguration;
import com.paytech.risk.decision.domain.variable.VariableRuleConfiguration;
import com.paytech.risk.decision.domainservice.repository.DecisionFlowEdgeRepository;
import com.paytech.risk.decision.domainservice.repository.DecisionFlowNodeRepository;
import com.paytech.risk.decision.domainservice.repository.DecisionFlowRootRepository;
import com.paytech.risk.decision.domainservice.repository.DecisionFlowVersionRepository;
import com.paytech.risk.decision.domainservice.repository.DecisionRuleSetRepository;
import com.paytech.risk.decision.domainservice.service.DecisionConfigurationNotFoundException;
import com.paytech.risk.decision.domainservice.service.DecisionFlowReferenceService;
import com.paytech.risk.decision.domainservice.service.DecisionRuleSetService;
import com.paytech.risk.decision.domainservice.service.RiskVariableService;
import com.paytech.risk.decision.service.processor.request.FlowDetailRequest;

import lombok.AllArgsConstructor;

/** 在 Owner 模块内组合只读事实，保持同一 REPEATABLE_READ 快照。 */
@Component
@AllArgsConstructor
public class FlowDetailProcessor extends AbstractProcessor<FlowDetailRequest, FlowDetailView> {
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

    /** 公共模板校验输入后进入本地只读快照，不获取资源锁或写锁。 */
    @Override
    protected FlowDetailView processRequest(FlowDetailRequest request) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return transaction.execute(status -> read(request));
    }

    /** 组合该查询需要的真实记录与目录，不改变业务事实。 */
    private FlowDetailView read(FlowDetailRequest request) {
        DecisionFlowRow root = roots.findById(request.getFlowId()).orElseThrow(DecisionConfigurationNotFoundException::new);
        if (request.getFlowVersionId() == null) { root.verifyCurrentReadable(); }
        DecisionFlowVersionRow selected = request.getFlowVersionId() == null ? null : versions.findById(request.getFlowVersionId())
            .orElseThrow(DecisionConfigurationNotFoundException::new);
        if (selected != null) { root.verifyOwnedVersion(selected); }
        DecisionFlowVersionRow head = versions.findById(root.currentFlowVersionId())
            .orElseThrow(() -> new ErrorException("流程持久化配置不完整或身份不一致"));
        root.verifyHead(head);
        if (selected == null) { selected = head; }
        DecisionPoint point = rules.findPoint(root.decisionPointId())
            .orElseThrow(() -> new ErrorException("流程持久化配置不完整或身份不一致"));
        var graph = assembler.assemble(point.pointCode(), root, selected,
            nodes.findByVersion(selected.flowVersionId()), edges.findByVersion(selected.flowVersionId()));
        DecisionFlowVersionRow version = selected;
        List<Reference> refs = graph.nodes().stream().filter(n -> "RULE_SET".equals(n.type()))
            .map(n -> references.describe(n.nodeKey(), n.ruleSetVersionId(), point.decisionPointId(),
                version.inputContractCode(), version.inputContractVersion())).toList();
        Set<String> codes = new TreeSet<>(VariableFlowConfiguration.references(graph));
        graph.nodes().stream().filter(n -> "RULE_SET".equals(n.type())).map(Node::ruleSetVersionId).distinct().sorted()
            .map(ruleService::detailForPersistedReference).forEach(rule -> codes.addAll(VariableRuleConfiguration.references(rule)));
        var catalog = variables.currentValues(codes, clock.instant().truncatedTo(ChronoUnit.MICROS));
        return new FlowDetailView(root.flowId(), point.pointCode(), root.flowCode(), root.status().getCode(), head.flowVersionId(),
            head.versionNo(), root.version(), version.flowVersionId(), version.versionNo(), version.flowName(),
            version.inputContractCode(), version.inputContractVersion(), version.createdBy(), version.createTime(),
            version.changeReason(), graph, refs, catalog);
    }
}
