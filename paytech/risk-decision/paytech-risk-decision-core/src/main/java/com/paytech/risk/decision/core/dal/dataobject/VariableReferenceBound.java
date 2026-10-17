package com.paytech.risk.decision.core.dal.dataobject;

import lombok.Value;
import lombok.experimental.Accessors;

/**
 * 引用扫描首次查询的分阶段行号上界。
 * <li>只读数据库投影；访问方法沿用字段名（fluent），构造参数顺序与 Mapper 构造映射一致。</li>
 */
@Value
@Accessors(fluent = true)
public class VariableReferenceBound {
    /** 固定扫描阶段名称。 */
    private final String phase;

    /** 初始最高行 ID，空表为零。 */
    private final long upperBound;
}
