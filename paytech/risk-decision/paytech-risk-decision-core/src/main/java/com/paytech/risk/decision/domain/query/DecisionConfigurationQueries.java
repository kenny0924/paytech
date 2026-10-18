package com.paytech.risk.decision.domain.query;

import java.util.List;

import com.paytech.risk.decision.domain.enums.*;

/** 字典引用的 Owner 业务查询模型。 */
public final class DecisionConfigurationQueries {
    /** 仅作为模型或转换方法的命名空间，不允许创建实例。 */
    private DecisionConfigurationQueries() { }
    /** 不可变配置的精确身份及当前引用事实。 */
    public record DictionaryReferenceView(long ruleSetId, String pointCode, String ruleSetCode, String ruleSetName, long ruleSetVersionId, int versionNo, String ruleCode, String ruleName, Integer actionSequenceNo, String targetBindingCode, RuleSetManagementStatus ruleSetStatus, boolean isCurrentConfiguration, DictionaryType referenceKind, List<DecisionEnvironment> activeEnvironments) { }
    /** 原因来源是互斥的规则项或流程结束节点，标签合同保持原状。 */
    public sealed interface ReasonReferenceView permits RuleReasonReferenceView, FlowEndReasonReferenceView { }
    public record RuleReasonReferenceView(DictionaryReferenceView rule) implements ReasonReferenceView { }
    public record FlowEndReasonReferenceView(String pointCode, long flowId, String flowCode, String flowName,
        long flowVersionId, int flowVersionNo, String nodeKey, String nodeName, FlowStatus flowStatus,
        boolean isCurrentConfiguration) implements ReasonReferenceView { }
}
