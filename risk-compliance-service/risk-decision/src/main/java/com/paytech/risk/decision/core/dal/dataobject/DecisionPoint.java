package com.paytech.risk.decision.core.dal.dataobject;

import com.paytech.risk.decision.domain.enums.DecisionBusinessDomain;
import com.paytech.risk.decision.domain.enums.DecisionPointStatus;
import java.time.Instant;

import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Accessors;

/**
 * 决策事件点的稳定身份。
 * <li>只读数据库投影；访问方法沿用字段名（fluent），构造参数顺序与 Mapper 构造映射一致。</li>
 */
@Value
@Accessors(fluent = true)
public class DecisionPoint {
    /** 风险决策事件点的稳定业务标识。 */
    private final long decisionPointId;

    /** Risk 受控目录中的事件点编码。 */
    @NonNull
    private final String pointCode;

    /** 事件点名称。 */
    @NonNull
    private final String pointName;

    /** 事件点所属业务域。 */
    @NonNull
    private final DecisionBusinessDomain businessDomain;

    /** 事件点当前状态。 */
    @NonNull
    private final DecisionPointStatus status;

    /** 记录的 UTC 微秒创建时间。 */
    @NonNull
    private final Instant createTime;

    /** 可变记录最后一次持久化更新的 UTC 微秒时间。 */
    @NonNull
    private final Instant updateTime;
}
