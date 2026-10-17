package com.paytech.risk.decision.domain.query;

import com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate;
import com.paytech.risk.decision.domain.variable.RiskVariable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 真实来源与规则事实组成的只读引用。 */
@Getter
@AllArgsConstructor
public class FlowRuleSetReferenceView {

    /** 实际来源类型。 */
    private final String sourceType;

    /** 实际来源准确版本。 */
    private final long sourceVersionId;

    /** 实际来源节点键。 */
    private final String nodeKey;

    /** 规则集当前管理状态。 */
    private final String ruleSetStatus;

    /** 真实绑定的准确规则版本。 */
    private final DecisionRuleSetAggregate ruleSet;

    /** 同一只读快照内的共享变量当前值。 */
    private final RiskVariable.Catalog catalog;
}
