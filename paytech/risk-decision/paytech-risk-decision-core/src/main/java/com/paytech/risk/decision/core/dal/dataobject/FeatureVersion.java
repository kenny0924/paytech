package com.paytech.risk.decision.core.dal.dataobject;

import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.ExecutionVersionStatus;
import java.time.Instant;

import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Accessors;

/**
 * 工程注册 Feature 的不可变执行版本。
 * <li>只读数据库投影；访问方法沿用字段名（fluent），构造参数顺序与 Mapper 构造映射一致。</li>
 */
@Value
@Accessors(fluent = true)
public class FeatureVersion {
    /** Feature 执行版本的稳定业务标识。 */
    private final long featureVersionId;

    /** Risk 受控目录中的 Feature 编码。 */
    @NonNull
    private final String featureCode;

    /** Feature 名称。 */
    @NonNull
    private final String featureName;

    /** Risk 受控目录中的业务分类。 */
    @NonNull
    private final String businessCategory;

    /** Feature 版本号。 */
    private final int versionNo;

    /** Feature 结果的闭合值类型。 */
    @NonNull
    private final DecisionValueType resultValueType;

    /** 工程注册 Handler 编码。 */
    @NonNull
    private final String handlerCode;

    /** Handler 实现版本。 */
    @NonNull
    private final String implementationVersion;

    /** Handler 实现的 SHA-256 小写十六进制哈希。 */
    @NonNull
    private final String implementationHash;

    /** Feature 超时上限，单位毫秒。 */
    private final int timeoutMillis;

    /** Feature 执行版本状态。 */
    @NonNull
    private final ExecutionVersionStatus status;

    /** 记录的 UTC 微秒创建时间。 */
    @NonNull
    private final Instant createTime;

    /** 可变记录最后一次持久化更新的 UTC 微秒时间。 */
    @NonNull
    private final Instant updateTime;
}
