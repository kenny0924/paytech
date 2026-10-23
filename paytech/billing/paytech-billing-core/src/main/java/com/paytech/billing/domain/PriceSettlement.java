package com.paytech.billing.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Client 价格的结算四字段（T+N 到期转可用时间的输入，写入 t_billing_fact_pricing）
 * <li>commercial 返回的任一字段可能为空；为空时事实停在重试（Q9），不在此对象内补默认值。</li>
 */
@Getter
@AllArgsConstructor
public class PriceSettlement {
    /** 非负工作日数 N */
    private final Integer settlementBusinessDays;

    /** 工作日日历代码，引用 t_business_calendar */
    private final String businessCalendarCode;

    /** 结算时区（IANA） */
    private final String settlementTimezone;

    /** 结算时区内的当日截止秒 */
    private final Integer cutoffSecondOfDay;

    /** 四个字段均已给出。 */
    public boolean complete() {
        return settlementBusinessDays != null && businessCalendarCode != null && settlementTimezone != null
            && cutoffSecondOfDay != null;
    }
}
