package com.paytech.risk.decision.core.dal.dataobject;

import com.paytech.risk.decision.domain.enums.ActionCategory;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.ExecutionVersionStatus;
import com.paytech.risk.decision.domain.enums.MergePolicy;
import java.time.Instant;

import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Accessors;

/**
 * 工程注册 Action 的不可变执行版本。
 * <li>只读数据库投影；访问方法沿用字段名（fluent），构造参数顺序与 Mapper 构造映射一致。</li>
 */
@Value
@Accessors(fluent = true)
public class ActionVersion {
    /** Action 执行版本的稳定业务标识。 */
    private final long actionVersionId;

    /** Risk 受控目录中的 Action 编码。 */
    @NonNull
    private final String actionCode;

    /** Action 名称。 */
    @NonNull
    private final String actionName;

    /** Action 的闭合业务分类。 */
    @NonNull
    private final ActionCategory actionCategory;

    /** Action 版本号。 */
    private final int versionNo;

    /** 工程注册 Handler 编码。 */
    private final String handlerCode;

    /** Handler 实现版本。 */
    private final String implementationVersion;

    /** Handler 实现的 SHA-256 小写十六进制哈希。 */
    private final String implementationHash;

    /** 是否产生终态业务决定。 */
    private final boolean terminalFlag;

    /** 终态 Action 的业务决定；非终态时为空。 */
    private final DecisionValue decisionCode;

    /** 相同目标 Action 的固定合并策略。 */
    @NonNull
    private final MergePolicy mergePolicy;

    /** 是否允许在 Shadow 中模拟。 */
    private final boolean shadowSupportedFlag;

    /** Action 执行版本状态。 */
    @NonNull
    private final ExecutionVersionStatus status;

    /** 记录的 UTC 微秒创建时间。 */
    @NonNull
    private final Instant createTime;

    /** 可变记录最后一次持久化更新的 UTC 微秒时间。 */
    @NonNull
    private final Instant updateTime;
}
