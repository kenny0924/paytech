package com.paytech.operations.console.bff.service.model.riskevaluation;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 条件树节点：分组（AND/OR/NOT）或谓词，附带本次评估的结果。
 */
@Getter
@Setter
public class RiskEvaluationConditionNode {
    /**
     * 节点键，即 Risk 条件节点 ID 的十进制文本
     */
    private String nodeKey;

    /**
     * 节点类型
     * <li>GROUP、PREDICATE。</li>
     */
    private String nodeType;

    /**
     * 分组运算符；仅分组节点有值
     * <li>AND、OR、NOT。</li>
     */
    private String groupOperator;

    /**
     * 比较运算符；仅谓词节点有值
     */
    private String comparisonOperator;

    /**
     * 本次评估的结果
     * <li>谓词取自 Risk 谓词轨迹；分组由子节点计算，任一子节点无结果则为 null；未求值为 null。</li>
     */
    private Boolean result;

    /**
     * 谓词操作数；分组节点为空数组
     */
    private List<RiskEvaluationOperand> operands;

    /**
     * 子节点，按规则集详情中的顺序；谓词节点为空数组
     */
    private List<RiskEvaluationConditionNode> children;
}
