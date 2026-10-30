package com.paytech.tax.api.response;


import lombok.Getter;
import lombok.Setter;

/** 一份税务注册（展示用，不含受控注册号引用） */
@Getter
@Setter
public class TaxRegistrationResponseBody {
    /** 注册 ID */
    private String registrationId;

    /** 纳税主体 Party ID */
    private String subjectPartyId;

    /** 辖区（国家代码） */
    private String jurisdictionCode;

    /** 税局代码 */
    private String authorityCode;

    /** 税种 */
    private String taxType;

    /** 制度代码，例如 STANDARD、NON_RESIDENT、SVRR */
    private String schemeCode;

    /** 票面展示的公开税号 */
    private String publicRegistrationNo;

    /** 申报币种 */
    private String reportingCurrency;

    /** 法定日期时区，例如 Asia/Riyadh */
    private String legalTimezone;

    /** 申报周期：MONTHLY、QUARTERLY */
    private String filingFrequency;

    /**
     * 谁操作该主体在该地区的税务（ManagedBy）
     * <li>取自计算组件引用的准入（t_tax_market.managed_by）；同一注册被多个准入引用时取最新修订；没有被引用时为空。</li>
     */
    private String managedBy;

    /** 注册状态：PENDING、ACTIVE、SUSPENDED、DEREGISTERED */
    private String status;
}
