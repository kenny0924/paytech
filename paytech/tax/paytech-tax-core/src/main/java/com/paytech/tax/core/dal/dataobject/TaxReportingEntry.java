package com.paytech.tax.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.paytech.tax.core.enums.ReportingEntryRole;

import lombok.Getter;
import lombok.Setter;

/** t_tax_reporting_entry：申报底稿；追加写、不可变。 */
@Getter
@Setter
public class TaxReportingEntry {
    /** Snowflake 主键 */
    private Long id;

    /** 交易 ID */
    private Long transactionId;

    /** 组件 ID */
    private Long componentId;

    /** 注册 ID */
    private Long registrationId;

    /** 期间 ID */
    private Long periodId;

    /** 申报角色 */
    private ReportingEntryRole entryRole;

    /** 申报规则代码 */
    private String reportingRuleCode;

    /** 申报规则包 ID */
    private Long reportingPolicyId;

    /** 原币 */
    private String sourceCurrency;

    /** 原币税基 */
    private BigDecimal sourceTaxBase;

    /** 原币税额 */
    private BigDecimal sourceTaxAmount;

    /** 申报币 */
    private String reportingCurrency;

    /** 汇率 */
    private BigDecimal fxRate;

    /** 汇率日期 */
    private LocalDate fxRateDate;

    /** 汇率来源 */
    private String fxSourceRef;

    /** 申报币税基 */
    private BigDecimal reportingTaxBase;

    /** 申报币税额 */
    private BigDecimal reportingTaxAmount;

    /** 税点日期 */
    private LocalDate taxPointDate;

    /** 创建时间 */
    private Instant createTime;
}
