package com.paytech.tax.api.response;


import lombok.Getter;
import lombok.Setter;

/** 冻结的一条国家证据 */
@Getter
@Setter
public class TaxEvidenceResponseBody {
    /** 证据类型（TaxEvidenceType） */
    private String type;

    /** 国家代码 */
    private String countryCode;

    /** 行政区代码；可空 */
    private String subdivisionCode;

    /** 是否为本次确定供应地所用的证据 */
    private Boolean placeOfSupplyBasis;
}
