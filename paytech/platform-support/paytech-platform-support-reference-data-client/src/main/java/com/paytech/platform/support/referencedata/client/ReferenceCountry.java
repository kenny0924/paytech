package com.paytech.platform.support.referencedata.client;

import com.paytech.platform.support.api.enums.ReferenceDataStatus;

/**
 * 国家与地区（只含后端判定所需字段；名称与国旗归前端展示）。
 *
 * @param code ISO 3166-1 alpha-2 代码
 * @param alpha3Code ISO 3166-1 alpha-3 代码
 * @param numericCode ISO 3166-1 三位数字码，保留前导零
 * @param regionCode 平台地理区域代码，如 GCC、MEA、EUROPE（开放集合）
 * @param callingCode 国际电话区号，不含加号；没有区号的地区为 null
 * @param defaultCurrencyCode 法定主币种 ISO 4217 代码；没有法定货币时为 null
 * @param status Platform 当前状态
 */
public record ReferenceCountry(String code, String alpha3Code, String numericCode, String regionCode,
    String callingCode, String defaultCurrencyCode, ReferenceDataStatus status) implements ReferenceItem {
}
