package com.paytech.tax.core.dal.dataobject;

import java.time.Instant;
import java.time.LocalDate;

import com.paytech.tax.core.enums.TaxPeriodStatus;

import lombok.Getter;
import lombok.Setter;

/** t_tax_period：注册下的法定申报期间。 */
@Getter
@Setter
public class TaxPeriod {
    /** Snowflake 主键 */
    private Long id;

    /** 注册 ID */
    private Long registrationId;

    /** 开始日（含） */
    private LocalDate periodStart;

    /** 结束日（含） */
    private LocalDate periodEnd;

    /** 申报截止日 */
    private LocalDate filingDueDate;

    /** 缴款截止日 */
    private LocalDate paymentDueDate;

    /** 申报币种 */
    private String reportingCurrency;

    /** 状态 */
    private TaxPeriodStatus status;

    /** 创建时间 */
    private Instant createTime;

    /** 更新时间 */
    private Instant updateTime;
}
