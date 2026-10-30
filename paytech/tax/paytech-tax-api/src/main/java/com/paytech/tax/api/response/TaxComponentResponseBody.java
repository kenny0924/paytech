package com.paytech.tax.api.response;


import lombok.Getter;
import lombok.Setter;

/** 一行上的一个税组件：依据、处理与金额 */
@Getter
@Setter
public class TaxComponentResponseBody {
    /** 组件 ID */
    private String componentId;

    /** 组件代码，例如 VAT */
    private String componentCode;

    /** 征税辖区（国家代码） */
    private String jurisdictionCode;

    /** 税种，例如 VAT */
    private String taxType;

    /** 处理类型（TaxTreatment） */
    private String treatment;

    /** 地区包原因码，例如 SA_REMOTE_B2C_STANDARD */
    private String reasonCode;

    /** 法定承担角色：SELLER、BUYER */
    private String liableRole;

    /** 依据的 market ID */
    private String marketId;

    /** 依据的注册 ID；NOT_COLLECTED 等无须注册时可空 */
    private String registrationId;

    /** 依据的地区包 ID */
    private String policyId;

    /** 命中的规则代码 */
    private String ruleCode;

    /** 税基 */
    private String taxBase;

    /** 税率（小数，例如 0.15）；无适用税率时为空，而不是 0 */
    private String rate;

    /** 本组件收取的税额 */
    private String taxAmount;

    /** 算法：本期 PERCENTAGE_EXCLUSIVE 或 PERCENTAGE_INCLUSIVE */
    private String calculationMethod;

    /** 取整方法，例如 HALF_UP */
    private String roundingMethod;

    /** 取整小数位 */
    private Integer roundingScale;
}
