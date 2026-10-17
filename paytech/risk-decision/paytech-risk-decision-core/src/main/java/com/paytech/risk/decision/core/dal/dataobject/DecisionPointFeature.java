package com.paytech.risk.decision.core.dal.dataobject;

import java.time.Instant;

import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Accessors;

/**
 * 事件点版本的 Feature 白名单绑定。
 * <li>只读数据库投影；访问方法沿用字段名（fluent），构造参数顺序与 Mapper 构造映射一致。</li>
 */
@Value
@Accessors(fluent = true)
public class DecisionPointFeature {
    /** Feature 白名单绑定的稳定业务标识。 */
    private final long bindingId;

    /** 所属事件点版本的稳定业务标识。 */
    private final long pointVersionId;

    /** 被允许 Feature 版本的稳定业务标识。 */
    private final long featureVersionId;

    /** Feature 的执行顺序。 */
    private final int sequenceNo;

    /** 记录的 UTC 微秒创建时间。 */
    @NonNull
    private final Instant createTime;
}
