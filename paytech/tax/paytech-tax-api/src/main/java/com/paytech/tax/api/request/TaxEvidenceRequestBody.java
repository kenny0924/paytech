package com.paytech.tax.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/** 一条国家维度证据：只含批准的派生维度，不含地址、IP 等原值。 */
@Getter
@Setter
public class TaxEvidenceRequestBody {
    /** 证据类型（TaxEvidenceType）：DECLARED_COUNTRY、BILLING_COUNTRY */
    @NotBlank
    @Pattern(regexp = "DECLARED_COUNTRY|BILLING_COUNTRY", message = "只能为 DECLARED_COUNTRY 或 BILLING_COUNTRY")
    private String type;

    /** ISO 3166-1 alpha-2 国家代码 */
    @NotBlank
    @Pattern(regexp = "[A-Z]{2}", message = "必须是 2 位大写字母")
    private String countryCode;

    /**
     * ISO 3166-2 行政区代码
     * <li>可空；本期不参与计算，只冻结。</li>
     */
    @Pattern(regexp = "[A-Z]{2}-[A-Z0-9]{1,3}", message = "必须是 ISO 3166-2 行政区代码")
    private String subdivisionCode;
}
