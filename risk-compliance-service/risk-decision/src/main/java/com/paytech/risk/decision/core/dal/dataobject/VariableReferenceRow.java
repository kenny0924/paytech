package com.paytech.risk.decision.core.dal.dataobject;

/** 变量反向引用候选，流程条件始终整树读取。 */
public record VariableReferenceRow(
    /** 本阶段实际扫描行的稳定标识。 */
    long scanId,
    /** RULE_SET、DECISION_FLOW 或 VARIABLE。 */
    String consumerType,
    /** 消费者根，缺失表示关联损坏。 */
    Long rootId,
    /** 精确保存版本；变量依赖为空。 */
    Long versionId,
    /** 业务版本号；变量依赖为空。 */
    Integer versionNo,
    /** 消费者永久编码。 */
    String code,
    /** 保存版本名称或变量当前名称。 */
    String name,
    /** 当前根版本为 CURRENT，其他包括已删根为 HISTORY。 */
    String scope,
    /** 操作数、动作或连线精确位置。 */
    String location,
    /** 只在 FLOW_EDGE 阶段携带有界完整条件树。 */
    String conditionJson) { }
