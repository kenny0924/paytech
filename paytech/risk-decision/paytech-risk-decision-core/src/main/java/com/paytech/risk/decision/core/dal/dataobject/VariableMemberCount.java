package com.paytech.risk.decision.core.dal.dataobject;

import lombok.Value;
import lombok.experimental.Accessors;

/**
 * 集合当前成员数量的数据库聚合投影。
 * <li>只读数据库投影；访问方法沿用字段名（fluent），构造参数顺序与 Mapper 构造映射一致。</li>
 */
@Value
@Accessors(fluent = true)
public class VariableMemberCount {
    /** 所属集合永久编码。 */
    private final String variableCode;

    /** 当前提交成员数，包括零条以外的聚合结果。 */
    private final long memberCount;
}
