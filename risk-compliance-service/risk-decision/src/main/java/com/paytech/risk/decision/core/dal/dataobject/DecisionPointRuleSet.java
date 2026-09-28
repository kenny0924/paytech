package com.paytech.risk.decision.core.dal.dataobject;

import java.time.Instant;
import java.util.Objects;

/** 事件点版本的有序 Rule Set Version 绑定。 */
public record DecisionPointRuleSet(
    /** Rule Set 绑定的稳定业务标识。 */
    long bindingId,
    /** 所属事件点版本的稳定业务标识。 */
    long pointVersionId,
    /** 被绑定规则集版本的稳定业务标识。 */
    long ruleSetVersionId,
    /** Rule Set 的执行顺序。 */
    int sequenceNo,
    /** 记录的 UTC 微秒创建时间。 */
    Instant createTime
) {
    public DecisionPointRuleSet {
        Objects.requireNonNull(createTime, "createTime");
    }
}
