package com.paytech.risk.decision.service.query;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.core.dal.dataobject.*;
import com.paytech.risk.decision.domain.authoring.DecisionPointAuthoringDescriptor;
import com.paytech.risk.decision.domain.enums.VariableSource;
import com.paytech.risk.decision.domain.flow.DecisionFlow;
import com.paytech.risk.decision.domain.flow.DecisionFlowGraphAssembler;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate;
import com.paytech.risk.decision.domain.runtime.ExecutionSnapshot;
import com.paytech.risk.decision.domain.variable.RiskVariable;
import com.paytech.risk.decision.domain.variable.VariableFlowConfiguration;
import com.paytech.risk.decision.domain.variable.VariableRuleConfiguration;
import com.paytech.risk.decision.domainservice.repository.*;
import com.paytech.risk.decision.domainservice.service.DecisionRuleSetService;
import com.paytech.risk.decision.domainservice.service.RiskVariableService;

import lombok.AllArgsConstructor;

/**
 * 读取执行发布的完整可执行内容；正式评估、发布校验与影子运行共用
 * <li>流程与规则集按发布冻结的版本读取并复核内容摘要；变量读取当前定义与成员。</li>
 * <li>不开启事务：冻结版本不可变，变量当前值允许观察相邻已提交状态。</li>
 */
@Component
@AllArgsConstructor
public class ExecutionSnapshotReader {
    private final RiskVariableService variables;
    private final DecisionRuleSetService ruleSets;
    private final DecisionFlowGraphAssembler assembler;
    private final DecisionRuleSetRepository rules;
    private final DecisionFlowRootRepository flowRoots;
    private final DecisionFlowNodeRepository flowNodes;
    private final DecisionFlowEdgeRepository flowEdges;
    private final DecisionFlowEndOutputRepository flowOutputs;
    private final DecisionFlowVersionRepository flowVersions;
    private final DecisionPointVersionRepository pointVersions;
    private final ListFeatureDefinitionRepository listFeatures;
    private final VelocityFeatureDefinitionRepository velocityFeatures;

    /**
     * 读取执行内容
     *
     * @param release 执行发布
     * @return 可直接交给决策引擎的快照
     *         <li>引用缺失、版本不属于发布决策点或内容摘要不一致时以系统错误抛出。</li>
     */
    public ExecutionSnapshot read(ExecutionRelease release) {
        DecisionPoint point = rules.findPoint(release.getDecisionPointId())
            .orElseThrow(() -> new ErrorException("发布的决策点不存在: " + release.getReleaseId()));
        DecisionPointAuthoringDescriptor descriptor = DecisionPointAuthoringDescriptor.require(point.pointCode());
        DecisionPointVersion pointVersion = pointVersions.findById(release.getPointVersionId())
            .filter(value -> value.decisionPointId() == point.decisionPointId())
            .orElseThrow(() -> new ErrorException("发布的决策点版本不存在或不属于该决策点: " + release.getReleaseId()));
        DecisionFlow.Graph graph = graph(point.pointCode(), release.getDecisionFlowVersionId());
        Map<Long, DecisionRuleSetAggregate> ruleSetsByVersion = ruleSets(graph, point.decisionPointId());
        Map<String, RiskVariable.Entry> entries = variables.definitions(references(graph, ruleSetsByVersion.values()));
        return new ExecutionSnapshot(release, descriptor, pointVersion.timeoutMillis(), graph, ruleSetsByVersion, entries, collections(entries),
            velocity(entries), lists(entries));
    }

    /** 按冻结的流程版本重建配置图。 */
    DecisionFlow.Graph graph(String pointCode, long flowVersionId) {
        DecisionFlowVersionRow version = flowVersions.findById(flowVersionId)
            .orElseThrow(() -> new ErrorException("发布的决策流版本不存在: " + flowVersionId));
        DecisionFlowRow root = flowRoots.findById(version.flowId())
            .orElseThrow(() -> new ErrorException("发布的决策流不存在: " + version.flowId()));
        return assembler.assemble(pointCode, root, version, flowNodes.findByVersion(flowVersionId),
            flowEdges.findByVersion(flowVersionId), flowOutputs.findByVersion(flowVersionId));
    }

    /** 流程引用的规则集版本必须属于同一决策点。 */
    Map<Long, DecisionRuleSetAggregate> ruleSets(DecisionFlow.Graph graph, long decisionPointId) {
        Map<Long, DecisionRuleSetAggregate> result = new HashMap<>();
        graph.nodes().stream().filter(node -> "RULE_SET".equals(node.type())).map(DecisionFlow.Node::ruleSetVersionId)
            .distinct().forEach(id -> {
                DecisionRuleSetAggregate aggregate = ruleSets.detailForPersistedReference(id);
                if (aggregate.version().decisionPointId() != decisionPointId) {
                    throw new ErrorException("规则集版本不属于发布决策点: " + id);
                }
                result.put(id, aggregate);
            });
        return result;
    }

    /** 连线条件与规则条件引用的全部变量编码。 */
    static Set<String> references(DecisionFlow.Graph graph, java.util.Collection<DecisionRuleSetAggregate> values) {
        Set<String> codes = new TreeSet<>(VariableFlowConfiguration.references(graph));
        values.forEach(aggregate -> codes.addAll(VariableRuleConfiguration.references(aggregate)));
        return codes;
    }

    /** 集合变量的当前全部成员规范值。 */
    Map<String, List<Object>> collections(Map<String, RiskVariable.Entry> entries) {
        return entries.values().stream().filter(entry -> entry.definition().sourceType() == VariableSource.COLLECTION)
            .collect(Collectors.toMap(entry -> entry.definition().variableCode(),
                entry -> variables.collection(entry).stream().map(RiskVariable.Member::value).toList()));
    }

    /** 实时累计变量的不可变参数。 */
    Map<Long, VelocityFeatureDefinition> velocity(Map<String, RiskVariable.Entry> entries) {
        return velocityFeatures.findByIds(featureIds(entries, VariableSource.VELOCITY)).stream()
            .collect(Collectors.toMap(VelocityFeatureDefinition::getFeatureVersionId, Function.identity()));
    }

    /** 名单命中变量的不可变参数。 */
    Map<Long, ListFeatureDefinition> lists(Map<String, RiskVariable.Entry> entries) {
        return listFeatures.findByIds(featureIds(entries, VariableSource.LIST)).stream()
            .collect(Collectors.toMap(ListFeatureDefinition::getFeatureVersionId, Function.identity()));
    }

    /** 指定来源变量关联的 Feature Version。 */
    static List<Long> featureIds(Map<String, RiskVariable.Entry> entries, VariableSource source) {
        return entries.values().stream().map(RiskVariable.Entry::definition).filter(d -> d.sourceType() == source)
            .map(RiskVariable.Definition::featureVersionId).distinct().sorted().toList();
    }
}
