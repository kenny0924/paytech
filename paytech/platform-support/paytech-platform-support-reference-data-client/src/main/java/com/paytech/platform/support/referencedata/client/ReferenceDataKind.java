package com.paytech.platform.support.referencedata.client;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 参考数据种类：消费方在 {@code platform-support.reference-data.client.kinds} 中按需开启。
 * <li>Code 用于配置、异常信息、日志与健康明细。</li>
 */
public enum ReferenceDataKind implements CodeEnum {
    /** 币种（ISO 4217），读取 {@code GET /platform/v1/reference-data/currencies}。 */
    CURRENCY("CURRENCY"),

    /** 国家与地区（ISO 3166-1），读取 {@code GET /platform/v1/reference-data/countries}。 */
    COUNTRY("COUNTRY"),

    /** 一级行政区（ISO 3166-2），读取 {@code GET /platform/v1/reference-data/country-subdivisions}。 */
    SUBDIVISION("SUBDIVISION"),

    /** 两级行业分类，读取 {@code GET /platform/v1/reference-data/industries}。 */
    INDUSTRY("INDUSTRY"),

    /** MCC（ISO 18245），分页读取 {@code GET /platform/v1/reference-data/mccs}。 */
    MCC("MCC");

    private final String code;

    ReferenceDataKind(String code) {
        this.code = code;
    }

    /** 稳定 Code。 */
    @Override
    public String getCode() {
        return code;
    }
}
