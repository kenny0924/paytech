package com.paytech.tax.api.response;


import lombok.Getter;
import lombok.Setter;

/** 一条申报底稿；原币金额按交易币种精度，申报金额按申报币种精度 */
@Getter
@Setter
public class TaxReportingEntryResponseBody {
    /** 底稿 ID */
    private String entryId;

    /** 来源组件 ID */
    private String componentId;

    /** 注册 ID */
    private String registrationId;

    /** 期间 ID */
    private String periodId;

    /** 期间开始日 yyyy-MM-dd */
    private String periodStart;

    /** 期间结束日（含）yyyy-MM-dd */
    private String periodEnd;

    /** 申报截止日 yyyy-MM-dd */
    private String filingDueDate;

    /** 申报角色：本期 OUTPUT */
    private String entryRole;

    /** 申报规则代码 */
    private String reportingRuleCode;

    /** 原币币种 */
    private String sourceCurrency;

    /** 原币税基 */
    private String sourceTaxBase;

    /** 原币税额 */
    private String sourceTaxAmount;

    /** 申报币种 */
    private String reportingCurrency;

    /** 原币 → 申报币汇率；同币种为 1 */
    private String fxRate;

    /** 汇率日期 yyyy-MM-dd */
    private String fxRateDate;

    /** 申报币税基 */
    private String reportingTaxBase;

    /** 申报币税额 */
    private String reportingTaxAmount;

    /** 税点日期 yyyy-MM-dd */
    private String taxPointDate;
}
