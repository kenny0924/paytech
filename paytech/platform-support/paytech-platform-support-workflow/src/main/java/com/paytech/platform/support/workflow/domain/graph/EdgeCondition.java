package com.paytech.platform.support.workflow.domain.graph;

import java.util.List;

/**
 * 排他网关一条出边的条件组：组内子句全部成立（且）时该边成立；“或”用多条边表达。
 *
 * <p>条件在网关 conditions 中的顺序就是判断优先级，第一个成立的边生效。</p>
 *
 * @param edgeKey 本网关的出边 key
 * @param all 子句列表，全部成立才算满足
 */
public record EdgeCondition(String edgeKey, List<ConditionClause> all) {

    /** 复制列表，保持不可变。 */
    public EdgeCondition {
        all = List.copyOf(all);
    }
}
