package com.paytech.risk.decision.core.dal.dataobject;

import java.time.Instant;

import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Accessors;

/**
 * Feature Version 对另一个 Feature Version 的有序依赖。
 * <li>只读数据库投影；访问方法沿用字段名（fluent），构造参数顺序与 Mapper 构造映射一致。</li>
 */
@Value
@Accessors(fluent = true)
public class FeatureDependency {
    /** Feature 依赖的稳定业务标识。 */
    private final long dependencyId;

    /** 发起依赖的 Feature 版本标识。 */
    private final long featureVersionId;

    /** 被依赖 Feature 版本标识。 */
    private final long dependencyFeatureVersionId;

    /** 依赖执行顺序。 */
    private final int sequenceNo;

    /** 该依赖是否必需。 */
    private final boolean requiredFlag;

    /** 记录的 UTC 微秒创建时间。 */
    @NonNull
    private final Instant createTime;
}
