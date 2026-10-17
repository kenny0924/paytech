package com.paytech.risk.decision.domain.runtime;

import java.util.List;
import java.util.Map;

import com.paytech.risk.decision.core.dal.dataobject.ExecutionRelease;
import com.paytech.risk.decision.core.dal.dataobject.ListFeatureDefinition;
import com.paytech.risk.decision.core.dal.dataobject.VelocityFeatureDefinition;
import com.paytech.risk.decision.domain.authoring.DecisionPointAuthoringDescriptor;
import com.paytech.risk.decision.domain.flow.DecisionFlow;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate;
import com.paytech.risk.decision.domain.variable.RiskVariable;

/**
 * 一个执行发布在评估时刻的完整可执行内容
 * <li>流程与规则集来自发布冻结的版本；常量与集合是读取时的当前值，不随发布冻结。</li>
 *
 * @param release
 *            执行发布
 * @param descriptor
 *            决策点合同；只用于发布校验，引擎执行不读取
 * @param timeoutMillis
 *            冻结的决策点版本超时毫秒数
 * @param graph
 *            冻结的决策流
 * @param ruleSets
 *            流程引用的冻结规则集，按规则集版本 ID 索引
 * @param variables
 *            规则与连线引用的变量及其依赖的当前定义
 * @param collections
 *            集合变量的当前成员规范值
 * @param velocityFeatures
 *            实时累计 Feature 参数，按 Feature Version 索引
 * @param listFeatures
 *            名单命中 Feature 参数，按 Feature Version 索引
 */
public record ExecutionSnapshot(ExecutionRelease release, DecisionPointAuthoringDescriptor descriptor,
    int timeoutMillis, DecisionFlow.Graph graph, Map<Long, DecisionRuleSetAggregate> ruleSets, Map<String, RiskVariable.Entry> variables,
    Map<String, List<Object>> collections, Map<Long, VelocityFeatureDefinition> velocityFeatures,
    Map<Long, ListFeatureDefinition> listFeatures) {

    /** 复制集合，防止调用方修改快照。 */
    public ExecutionSnapshot {
        ruleSets = Map.copyOf(ruleSets);
        variables = Map.copyOf(variables);
        collections = Map.copyOf(collections);
        velocityFeatures = Map.copyOf(velocityFeatures);
        listFeatures = Map.copyOf(listFeatures);
    }
}
