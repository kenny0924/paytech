package com.paytech.tax.api.response;


import lombok.Getter;
import lombok.Setter;

/** 按辖区 + 注册 + 纳税主体汇总的应收税组件，与 OrderPaymentSucceeded.taxComponents 一一对应 */
@Getter
@Setter
public class TaxComponentSummaryResponseBody {
    /** 征税辖区（国家代码） */
    private String jurisdictionCode;

    /** 税种，例如 VAT */
    private String taxType;

    /** 注册 ID */
    private String registrationId;

    /** 纳税主体 Party ID（billing SELLER_TAX 行的收方 ID） */
    private String taxSubjectPartyId;

    /** 汇总税额，大于 0，按币种精度补齐 */
    private String taxAmount;
}
