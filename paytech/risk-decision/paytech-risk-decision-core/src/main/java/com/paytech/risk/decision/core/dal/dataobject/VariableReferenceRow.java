package com.paytech.risk.decision.core.dal.dataobject;

import lombok.Value;
import lombok.experimental.Accessors;

/**
 * 变量反向引用候选，流程条件始终整树读取。
 * <li>只读数据库投影；访问方法沿用字段名（fluent），构造参数顺序与 Mapper 构造映射一致。</li>
 */
@Value
@Accessors(fluent = true)
public class VariableReferenceRow {
    /** 本阶段实际扫描行的稳定标识。 */
    private final long scanId;

    /** RULE_SET、DECISION_FLOW 或 VARIABLE。 */
    private final String consumerType;

    /** 消费者根，缺失表示关联损坏。 */
    private final Long rootId;

    /** 精确保存版本；变量依赖为空。 */
    private final Long versionId;

    /** 业务版本号；变量依赖为空。 */
    private final Integer versionNo;

    /** 消费者永久编码。 */
    private final String code;

    /** 保存版本名称或变量当前名称。 */
    private final String name;

    /** 当前根版本为 CURRENT，其他包括已删根为 HISTORY。 */
    private final String scope;

    /** 操作数、动作或连线精确位置。 */
    private final String location;

    /** 只在 FLOW_EDGE 阶段携带有界完整条件树。 */
    private final String conditionJson;
}
