package com.paytech.operations.console.bff.service.model.riskevaluation;

import lombok.Getter;
import lombok.Setter;

/**
 * 流程路径中的一个节点；名称取自评估所用的冻结流程版本。
 */
@Getter
@Setter
public class RiskEvaluationPathStep {
    /**
     * 经过顺序，从 1 开始
     */
    private int stepNo;

    /**
     * 流程版本内的节点键
     */
    private String nodeKey;

    /**
     * 节点类型
     * <li>START、RULE_SET、END。</li>
     */
    private String nodeType;

    /**
     * 节点名称
     */
    private String nodeName;

    /**
     * 规则集节点引用的规则集版本 ID
     */
    private String ruleSetVersionId;

    /**
     * 规则集编码
     */
    private String ruleSetCode;

    /**
     * 规则集版本号
     */
    private Integer ruleSetVersionNo;

    /**
     * 规则集节点是否有规则命中
     */
    private Boolean matched;

    /**
     * 规则集节点的首个终态决定，或结束节点的结论
     */
    private String decision;

    /**
     * 是否复用了同一规则集版本此前的结果
     */
    private Boolean reused;

    /**
     * 离开本节点所走的连线键；结束节点为空
     */
    private String exitEdgeKey;

    /**
     * 连线名称
     */
    private String exitEdgeName;
}
