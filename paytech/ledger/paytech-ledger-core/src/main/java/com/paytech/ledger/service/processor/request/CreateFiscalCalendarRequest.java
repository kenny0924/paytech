package com.paytech.ledger.service.processor.request;

import com.paytech.ledger.core.enums.LedgerEntityType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 创建主体财务日历。 */
@Getter
@Setter
public class CreateFiscalCalendarRequest {
    /** 主体类型 */
    @NotNull
    private LedgerEntityType entityType;

    /** 主体标识 */
    @NotBlank
    private String entityId;

    /** 日历代码 */
    @NotBlank
    private String calendarCode;

    /** IANA 时区 */
    @NotBlank
    private String timezone;

    /** 财年开始月份，1—12 */
    @NotNull
    private Integer yearStartMonth;
}
