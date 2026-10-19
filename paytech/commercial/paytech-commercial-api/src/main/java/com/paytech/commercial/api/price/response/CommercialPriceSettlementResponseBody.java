package com.paytech.commercial.api.price.response;

import lombok.Getter;
import lombok.Setter;

/** Client 条款结算输入，与费用取自同一命中价格。 */
@Getter
@Setter
public class CommercialPriceSettlementResponseBody {
    /** T+N 工作日结算天数 */
    private Integer settlementBusinessDays;

    /** 工作日日历代码 */
    private String businessCalendarCode;

    /** 结算时区（IANA） */
    private String settlementTimezone;

    /** 结算基准日的本地日内截止秒数 */
    private Integer cutoffSecondOfDay;
}
