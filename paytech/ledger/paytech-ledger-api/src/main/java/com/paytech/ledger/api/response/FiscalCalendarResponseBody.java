package com.paytech.ledger.api.response;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 财务日历列表项。 */
@Getter
@Setter
public class FiscalCalendarResponseBody {
    /** 财务日历主键。 */
    private String fiscalCalendarId;

    /**
     * 主体类型
     * <li>OPERATING_ENTITY 或 FINANCE_ENTITY。</li>
     */
    private String entityType;

    /**
     * 主体标识
     * <li>OE 为 entityNo，Finance Entity 为本地主键。</li>
     */
    private String entityId;

    /** 日历代码。 */
    private String calendarCode;

    /** 日历时区，IANA 时区标识。 */
    private String timezone;

    /**
     * 财年起始月份
     * <li>1 到 12。</li>
     */
    private Integer yearStartMonth;

    /** 创建时间（UTC）。 */
    private Instant createTime;
}
