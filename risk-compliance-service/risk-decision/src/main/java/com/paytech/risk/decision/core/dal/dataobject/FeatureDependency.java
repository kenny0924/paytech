package com.paytech.risk.decision.core.dal.dataobject;

import java.time.Instant;
import java.util.Objects;

/** Feature Version 对另一个 Feature Version 的有序依赖。 */
public record FeatureDependency(
    /** Feature 依赖的稳定业务标识。 */
    long dependencyId,
    /** 发起依赖的 Feature 版本标识。 */
    long featureVersionId,
    /** 被依赖 Feature 版本标识。 */
    long dependencyFeatureVersionId,
    /** 依赖执行顺序。 */
    int sequenceNo,
    /** 该依赖是否必需。 */
    boolean requiredFlag,
    /** 记录的 UTC 微秒创建时间。 */
    Instant createTime
) {
    public FeatureDependency {
        Objects.requireNonNull(createTime, "createTime");
    }
}
