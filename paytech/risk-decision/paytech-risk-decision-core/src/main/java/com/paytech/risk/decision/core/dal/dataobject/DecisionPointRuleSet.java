package com.paytech.risk.decision.core.dal.dataobject;

import java.time.Instant;

import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Accessors;

/**
 * 事件点版本的有序 Rule Set Version 绑定。
 * <li>只读数据库投影；访问方法沿用字段名（fluent），构造参数顺序与 Mapper 构造映射一致。</li>
 */
@Value
@Accessors(fluent = true)
public class DecisionPointRuleSet {
    /** Rule Set 绑定的稳定业务标识。 */
    private final long bindingId;

    /** 所属事件点版本的稳定业务标识。 */
    private final long pointVersionId;

    /** 被绑定规则集版本的稳定业务标识。 */
    private final long ruleSetVersionId;

    /** Rule Set 的执行顺序。 */
    private final int sequenceNo;

    /** 记录的 UTC 微秒创建时间。 */
    @NonNull
    private final Instant createTime;
}
