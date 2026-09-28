package com.paytech.risk.decision.core.dal.dataobject;

import com.paytech.risk.decision.domain.enums.DecisionBusinessDomain;
import com.paytech.risk.decision.domain.enums.DecisionPointStatus;
import java.time.Instant;
import java.util.Objects;

/** 决策事件点的稳定身份。 */
public record DecisionPoint(
    /** 风险决策事件点的稳定业务标识。 */
    long decisionPointId,
    /** Risk 受控目录中的事件点编码。 */
    String pointCode,
    /** 事件点名称。 */
    String pointName,
    /** 事件点所属业务域。 */
    DecisionBusinessDomain businessDomain,
    /** 事件点当前状态。 */
    DecisionPointStatus status,
    /** 记录的 UTC 微秒创建时间。 */
    Instant createTime,
    /** 可变记录最后一次持久化更新的 UTC 微秒时间。 */
    Instant updateTime
) {
    public DecisionPoint {
        Objects.requireNonNull(pointCode, "pointCode");
        Objects.requireNonNull(pointName, "pointName");
        Objects.requireNonNull(businessDomain, "businessDomain");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createTime, "createTime");
        Objects.requireNonNull(updateTime, "updateTime");
    }
}
