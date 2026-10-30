package com.paytech.risk.decision.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.paytech.risk.decision.domain.enums.VelocityEventCode;

import lombok.Data;

/** Client 按 UTC 日的放行金额汇总（t_client_allowed_daily_total），每个 Client、事件、币种、UTC 日一行。 */
@Data
public class ClientAllowedDailyTotal {
    /** 汇总行 ID；累加到已有行时不使用。 */
    private long dailyTotalId;

    /** Client ID（CLIENT 累计主体键）。 */
    private long clientId;

    /** 放行事件：PAYMENT_ATTEMPT_ALLOWED 或 CHECKOUT_ALLOWED。 */
    private VelocityEventCode eventCode;

    /** 事件币种（ISO 4217），各币种分别汇总。 */
    private String currency;

    /** 评估时间所在的 UTC 自然日。 */
    private LocalDate eventDate;

    /**
     * 金额
     * <li>写入时为本次增量，读取时为当日合计。</li>
     */
    private BigDecimal amount;

    /**
     * 笔数
     * <li>写入时为本次增量 1，读取时为当日合计。</li>
     */
    private int eventCount;

    /** 创建 UTC 时间。 */
    private Instant createTime;

    /** 更新 UTC 时间。 */
    private Instant updateTime;
}
