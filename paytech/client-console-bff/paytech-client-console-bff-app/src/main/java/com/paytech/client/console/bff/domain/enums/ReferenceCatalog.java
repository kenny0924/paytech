package com.paytech.client.console.bff.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * client-master 采集 Schema 中引用 Platform 参考数据的保留目录码（S-04）
 * <li>client-master 不下发这些目录的条目，由 BFF 返回 form-schema 时从 Platform 补全。</li>
 */
public enum ReferenceCatalog implements CodeEnum {
    /** 币种（t_ref_currency）。 */
    REF_CURRENCY("REF_CURRENCY"),

    /** 一级行政区（t_ref_country_subdivision）。 */
    REF_COUNTRY_SUBDIVISION("REF_COUNTRY_SUBDIVISION");

    private final String code;

    ReferenceCatalog(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
