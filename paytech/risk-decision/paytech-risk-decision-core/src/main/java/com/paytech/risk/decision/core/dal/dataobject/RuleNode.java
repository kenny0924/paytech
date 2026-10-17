package com.paytech.risk.decision.core.dal.dataobject;

import java.time.Instant;
import com.paytech.risk.decision.domain.enums.ComparisonOperator;
import com.paytech.risk.decision.domain.enums.GroupOperator;
import com.paytech.risk.decision.domain.enums.NodeType;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;

/** 风险规则条件 AST 的节点。 */
@Getter
@AllArgsConstructor
@EqualsAndHashCode
@Accessors(fluent = true)
public class RuleNode {

    /** AST 节点的稳定业务标识。 */
    private long nodeId;

    /** 所属风险规则的稳定业务标识。 */
    private long ruleId;

    /** 同一规则内的父节点标识；根节点为空。 */
    private Long parentNodeId;

    /** 节点的闭合类型。 */
    @NonNull
    private NodeType nodeType;

    /** GROUP 节点的操作符；PREDICATE 时为空。 */
    private GroupOperator groupOperator;

    /** PREDICATE 节点的比较操作符；GROUP 时为空。 */
    private ComparisonOperator comparisonOperator;

    /** 同级节点顺序。 */
    private int sequenceNo;

    /** 记录的 UTC 微秒创建时间。 */
    @NonNull
    private Instant createTime;

    /** 可变记录最后一次持久化更新的 UTC 微秒时间。 */
    @NonNull
    private Instant updateTime;

}
