package com.paytech.risk.decision.core.dal.dataobject;

import java.time.Instant;
import java.util.Objects;

/** 事件点版本的 Feature 白名单绑定。 */
public record DecisionPointFeature(
    /** Feature 白名单绑定的稳定业务标识。 */
    long bindingId,
    /** 所属事件点版本的稳定业务标识。 */
    long pointVersionId,
    /** 被允许 Feature 版本的稳定业务标识。 */
    long featureVersionId,
    /** Feature 的执行顺序。 */
    int sequenceNo,
    /** 记录的 UTC 微秒创建时间。 */
    Instant createTime
) {
    public DecisionPointFeature {
        Objects.requireNonNull(createTime, "createTime");
    }
}
