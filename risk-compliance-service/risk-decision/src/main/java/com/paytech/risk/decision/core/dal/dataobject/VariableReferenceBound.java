package com.paytech.risk.decision.core.dal.dataobject;

/** 引用扫描首次查询的分阶段行号上界。 */
public record VariableReferenceBound(
    /** 固定扫描阶段名称。 */
    String phase,
    /** 初始最高行 ID，空表为零。 */
    long upperBound) { }
