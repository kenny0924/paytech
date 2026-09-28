package com.paytech.risk.decision.core.dal.dataobject;

import com.paytech.risk.decision.domain.enums.ActionCategory;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.ExecutionVersionStatus;
import com.paytech.risk.decision.domain.enums.MergePolicy;
import java.time.Instant;
import java.util.Objects;

/** 工程注册 Action 的不可变执行版本。 */
public record ActionVersion(
    /** Action 执行版本的稳定业务标识。 */
    long actionVersionId,
    /** Risk 受控目录中的 Action 编码。 */
    String actionCode,
    /** Action 名称。 */
    String actionName,
    /** Action 的闭合业务分类。 */
    ActionCategory actionCategory,
    /** Action 版本号。 */
    int versionNo,
    /** 工程注册 Handler 编码。 */
    String handlerCode,
    /** Handler 实现版本。 */
    String implementationVersion,
    /** Handler 实现的 SHA-256 小写十六进制哈希。 */
    String implementationHash,
    /** 是否产生终态业务决定。 */
    boolean terminalFlag,
    /** 终态 Action 的业务决定；非终态时为空。 */
    DecisionValue decisionCode,
    /** 相同目标 Action 的固定合并策略。 */
    MergePolicy mergePolicy,
    /** 是否允许在 Shadow 中模拟。 */
    boolean shadowSupportedFlag,
    /** Action 执行版本状态。 */
    ExecutionVersionStatus status,
    /** 记录的 UTC 微秒创建时间。 */
    Instant createTime,
    /** 可变记录最后一次持久化更新的 UTC 微秒时间。 */
    Instant updateTime
) {
    public ActionVersion {
        Objects.requireNonNull(actionCode, "actionCode");
        Objects.requireNonNull(actionName, "actionName");
        Objects.requireNonNull(actionCategory, "actionCategory");
        Objects.requireNonNull(mergePolicy, "mergePolicy");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createTime, "createTime");
        Objects.requireNonNull(updateTime, "updateTime");
    }
}
