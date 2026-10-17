package com.paytech.operations.console.bff.service.model.riskevaluation;

import lombok.Getter;
import lombok.Setter;

/**
 * Risk 实际求值的一个谓词结果；nodeId 等于规则集详情中条件节点的 nodeKey。
 */
@Getter
@Setter
public class RiskEvaluationPredicateItem {
    /**
     * 条件节点 ID（十进制文本）
     */
    private String nodeId;

    /**
     * 谓词是否成立
     */
    private boolean result;
}
