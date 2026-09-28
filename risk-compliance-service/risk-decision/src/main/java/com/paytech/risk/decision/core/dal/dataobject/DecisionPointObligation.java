package com.paytech.risk.decision.core.dal.dataobject;

import com.paytech.risk.decision.domain.enums.DecisionObligationFailurePolicy;
import com.paytech.risk.decision.domain.enums.DecisionObligationType;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import java.time.Instant;
import java.util.Objects;

/** 事件点版本工程绑定的系统义务。 */
public record DecisionPointObligation(
    /** 系统义务的稳定业务标识。 */
    long obligationId,
    /** 所属事件点版本的稳定业务标识。 */
    long pointVersionId,
    /** 触发该义务的业务决定。 */
    DecisionValue triggerDecision,
    /** 系统义务的闭合类型。 */
    DecisionObligationType obligationType,
    /** ACTION 义务绑定的 Action 版本；其他类型为空。 */
    Long actionVersionId,
    /** 同一触发决定下的执行顺序。 */
    int sequenceNo,
    /** 义务执行失败策略。 */
    DecisionObligationFailurePolicy failurePolicy,
    /** 义务是否启用。 */
    boolean enabledFlag,
    /** 记录的 UTC 微秒创建时间。 */
    Instant createTime
) {
    public DecisionPointObligation {
        Objects.requireNonNull(triggerDecision, "triggerDecision");
        Objects.requireNonNull(obligationType, "obligationType");
        Objects.requireNonNull(failurePolicy, "failurePolicy");
        Objects.requireNonNull(createTime, "createTime");
    }
}
