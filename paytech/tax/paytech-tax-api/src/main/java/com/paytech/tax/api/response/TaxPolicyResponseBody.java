package com.paytech.tax.api.response;


import lombok.Getter;
import lombok.Setter;

/** 一个地区包版本（计算依据） */
@Getter
@Setter
public class TaxPolicyResponseBody {
    /** 地区包 ID */
    private String policyId;

    /** 包代码，例如 DEMO_SA_SAAS */
    private String packageCode;

    /** 包版本 */
    private Integer revision;

    /** 辖区 */
    private String jurisdictionCode;

    /** 税种 */
    private String taxType;

    /** 制度代码 */
    private String schemeCode;

    /** 生效日 yyyy-MM-dd */
    private String effectiveFrom;

    /** 失效日（不含）yyyy-MM-dd；可空 */
    private String effectiveTo;

    /** 状态：DRAFT、APPROVED、RETIRED */
    private String status;
}
