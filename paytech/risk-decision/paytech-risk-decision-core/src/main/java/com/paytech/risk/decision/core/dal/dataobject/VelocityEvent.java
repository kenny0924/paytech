package com.paytech.risk.decision.core.dal.dataobject;

import com.paytech.risk.decision.domain.enums.VelocityEventCode;
import java.math.BigDecimal;
import java.time.Instant;

import lombok.Data;

/** 可计数计额的不可变风险事件（t_velocity_event）。 */
@Data
public class VelocityEvent {
    /** 事件 ID。 */
    private long velocityEventId;

    /** 事件编码。 */
    private VelocityEventCode eventCode;

    /** 来源类型，支付评估固定为 TRANSACTION_RISK_ASSESSMENT。 */
    private String sourceType;

    /** 来源事实 ID，即交易评估 ID。 */
    private long sourceId;

    /** 来源版本，交易评估不可变固定为 1。 */
    private int sourceVersion;

    /** 事件时间，等于评估时间。 */
    private Instant occurredTime;

    /** 事件金额，币种主单位。 */
    private BigDecimal amount;

    /** 事件币种。 */
    private String currency;

    /** 事件内容 SHA-256。 */
    private String eventHash;

    /** 创建 UTC 时间。 */
    private Instant createTime;
}
