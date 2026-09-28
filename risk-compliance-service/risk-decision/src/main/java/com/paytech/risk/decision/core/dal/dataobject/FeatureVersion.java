package com.paytech.risk.decision.core.dal.dataobject;

import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.ExecutionVersionStatus;
import java.time.Instant;
import java.util.Objects;

/** 工程注册 Feature 的不可变执行版本。 */
public record FeatureVersion(
    /** Feature 执行版本的稳定业务标识。 */
    long featureVersionId,
    /** Risk 受控目录中的 Feature 编码。 */
    String featureCode,
    /** Feature 名称。 */
    String featureName,
    /** Risk 受控目录中的业务分类。 */
    String businessCategory,
    /** Feature 版本号。 */
    int versionNo,
    /** Feature 结果的闭合值类型。 */
    DecisionValueType resultValueType,
    /** 工程注册 Handler 编码。 */
    String handlerCode,
    /** Handler 实现版本。 */
    String implementationVersion,
    /** Handler 实现的 SHA-256 小写十六进制哈希。 */
    String implementationHash,
    /** Feature 超时上限，单位毫秒。 */
    int timeoutMillis,
    /** Feature 执行版本状态。 */
    ExecutionVersionStatus status,
    /** 记录的 UTC 微秒创建时间。 */
    Instant createTime,
    /** 可变记录最后一次持久化更新的 UTC 微秒时间。 */
    Instant updateTime
) {
    public FeatureVersion {
        Objects.requireNonNull(featureCode, "featureCode");
        Objects.requireNonNull(featureName, "featureName");
        Objects.requireNonNull(businessCategory, "businessCategory");
        Objects.requireNonNull(resultValueType, "resultValueType");
        Objects.requireNonNull(handlerCode, "handlerCode");
        Objects.requireNonNull(implementationVersion, "implementationVersion");
        Objects.requireNonNull(implementationHash, "implementationHash");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createTime, "createTime");
        Objects.requireNonNull(updateTime, "updateTime");
    }
}
