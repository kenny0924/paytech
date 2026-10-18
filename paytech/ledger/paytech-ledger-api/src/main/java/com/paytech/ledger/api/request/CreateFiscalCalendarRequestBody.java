package com.paytech.ledger.api.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 创建财务日历请求。 */
@Getter
@Setter
public class CreateFiscalCalendarRequestBody {
    /**
     * 日历主体类型
     * <li>OPERATING_ENTITY 或 FINANCE_ENTITY</li>
     */
    @NotBlank
    @Pattern(regexp = "OPERATING_ENTITY|FINANCE_ENTITY", message = "只能为 OPERATING_ENTITY 或 FINANCE_ENTITY")
    private String entityType;

    /**
     * 主体标识
     * <li>规则同账簿；每个主体只有一套日历</li>
     */
    @NotBlank
    @Size(max = 64)
    private String entityId;

    /**
     * 日历代码
     */
    @NotBlank
    @Size(max = 64)
    private String calendarCode;

    /**
     * 确定财务日期边界的 IANA 时区
     * <li>例如 Asia/Dubai</li>
     */
    @NotBlank
    @Size(max = 64)
    private String timezone;

    /**
     * 财年开始月份
     * <li>1—12</li>
     */
    @NotNull
    @Min(1)
    @Max(12)
    private Integer yearStartMonth;
}
