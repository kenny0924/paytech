package com.paytech.tax.core.dal.dataobject;

import java.time.Instant;
import java.time.LocalDate;

import com.paytech.tax.core.enums.FilingFrequency;
import com.paytech.tax.core.enums.TaxRegistrationStatus;

import lombok.Getter;
import lombok.Setter;

/** t_tax_registration：纳税主体的一份法定注册与申报制度。 */
@Getter
@Setter
public class TaxRegistration {
    /** Snowflake 主键 */
    private Long id;

    /** 纳税主体 Party ID */
    private Long subjectPartyId;

    /** 辖区国家代码 */
    private String jurisdictionCode;

    /** 税局代码 */
    private String authorityCode;

    /** 税种 */
    private String taxType;

    /** 制度代码 */
    private String schemeCode;

    /** 受控注册号引用 */
    private String registrationNoRef;

    /** 票面公开税号 */
    private String publicRegistrationNo;

    /** 注册登记的主体法定名称（票面卖方名称） */
    private String registeredName;

    /** 生效日（含） */
    private LocalDate effectiveFrom;

    /** 失效日（不含）；可空 */
    private LocalDate effectiveTo;

    /** 状态 */
    private TaxRegistrationStatus status;

    /** 申报币种 */
    private String reportingCurrency;

    /** 法定 IANA 时区 */
    private String legalTimezone;

    /** 申报周期 */
    private FilingFrequency filingFrequency;

    /** 创建时间 */
    private Instant createTime;

    /** 更新时间 */
    private Instant updateTime;
}
