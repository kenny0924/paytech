package com.paytech.operations.console.bff.service.model.riskevaluation;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

/**
 * 实际执行过的一条规则及其条件树。
 */
@Getter
@Setter
public class RiskEvaluationRuleItem {
    /**
     * 规则集版本 ID
     */
    private String ruleSetVersionId;

    /**
     * 规则 ID
     */
    private String ruleId;

    /**
     * 规则编码；在规则集版本内唯一，用于对应规则集详情中的规则
     */
    private String ruleCode;

    /**
     * 规则名称
     */
    private String ruleName;

    /**
     * 优先级
     */
    private int priorityNo;

    /**
     * 是否命中
     */
    private boolean matched;

    /**
     * 是否终态
     */
    private boolean terminal;

    /**
     * 动作编码
     */
    private String action;

    /**
     * 条件树是否可用
     * <li>规则集详情读取失败，或规则、谓词节点与详情对不上时为 false，此时 condition 为 null。</li>
     */
    private boolean conditionAvailable;

    /**
     * 条件树根节点
     */
    private RiskEvaluationConditionNode condition;

    /**
     * Risk 谓词轨迹，仅用于组装条件树中各谓词的结果，不返回页面
     */
    @JsonIgnore
    private List<RiskEvaluationPredicateItem> predicates;
}
