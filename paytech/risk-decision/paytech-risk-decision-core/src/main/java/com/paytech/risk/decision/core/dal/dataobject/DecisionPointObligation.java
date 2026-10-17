package com.paytech.risk.decision.core.dal.dataobject;

import com.paytech.risk.decision.domain.enums.DecisionObligationFailurePolicy;
import com.paytech.risk.decision.domain.enums.DecisionObligationType;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import java.time.Instant;

import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Accessors;

/**
 * 事件点版本工程绑定的系统义务。
 * <li>只读数据库投影；访问方法沿用字段名（fluent），构造参数顺序与 Mapper 构造映射一致。</li>
 */
@Value
@Accessors(fluent = true)
public class DecisionPointObligation {
    /** 系统义务的稳定业务标识。 */
    private final long obligationId;

    /** 所属事件点版本的稳定业务标识。 */
    private final long pointVersionId;

    /** 触发该义务的业务决定。 */
    @NonNull
    private final DecisionValue triggerDecision;

    /** 系统义务的闭合类型。 */
    @NonNull
    private final DecisionObligationType obligationType;

    /** ACTION 义务绑定的 Action 版本；其他类型为空。 */
    private final Long actionVersionId;

    /** 同一触发决定下的执行顺序。 */
    private final int sequenceNo;

    /** 义务执行失败策略。 */
    @NonNull
    private final DecisionObligationFailurePolicy failurePolicy;

    /** 义务是否启用。 */
    private final boolean enabledFlag;

    /** 记录的 UTC 微秒创建时间。 */
    @NonNull
    private final Instant createTime;
}
