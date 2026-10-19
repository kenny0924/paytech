package com.paytech.ledger.api.response;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/** 会计期间列表项。 */
@Getter
@Setter
public class AccountingPeriodResponseBody {
    /** 会计期间主键。 */
    private String accountingPeriodId;

    /** 所属财务日历主键。 */
    private String fiscalCalendarId;

    /** 财年。 */
    private Integer fiscalYear;

    /** 财年内期号，从 1 开始。 */
    private Integer periodNo;

    /** 期间起始日期（含），日历时区本地日期。 */
    private LocalDate startDate;

    /** 期间结束日期（含），日历时区本地日期。 */
    private LocalDate endDate;

    /**
     * 期间状态
     * <li>FUTURE、OPEN、CLOSING、CLOSED 或 REOPENING。</li>
     */
    private String status;

    /** 已重开次数，从 0 开始。 */
    private Integer reopenCount;
}
