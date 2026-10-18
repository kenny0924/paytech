package com.paytech.risk.decision.domain.variable;

import java.util.List;

/** 变量引用查询结果，不持久化影子关系或游标。 */
public final class VariableReferences {
    /** 仅提供业务模型命名空间。 */
    private VariableReferences() { }
    /** 精确当前/历史消费者位置；变量消费者的版本坐标为空。 */
    public record Reference(String consumerType, long rootId, Long versionId, Integer versionNo,
        String code, String name, String scope, String location, String variableCode) { }
    /** 每页最多扫描一千候选行，空页可继续扫描。 */
    public record Page(List<Reference> items, String nextCursor, boolean hasMore, int scannedRows) { }
}
