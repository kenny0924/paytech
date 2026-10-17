package com.paytech.ledger.service.processor.request;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 在财务日历下创建 FUTURE 期间。 */
@Getter
@Setter
public class CreateAccountingPeriodRequest {
    /** 财务日历主键 */
    @NotNull
    private Long fiscalCalendarId;

    /** 财务年度 */
    @NotNull
    private Integer fiscalYear;

    /** 年度内期号 */
    @NotNull
    private Integer periodNo;

    /** 首个业务日期 */
    @NotNull
    private LocalDate startDate;

    /** 最后一个业务日期 */
    @NotNull
    private LocalDate endDate;
}
