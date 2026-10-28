package com.paytech.platform.support.referencedata.client;

import com.paytech.platform.support.api.enums.CurrencyRoundingMode;
import com.paytech.platform.support.api.enums.ReferenceDataStatus;

/**
 * 币种（只含后端判定所需字段；名称、符号与图标归前端展示）。
 *
 * @param code ISO 4217 三位大写字母代码
 * @param numericCode ISO 4217 三位数字码，保留前导零
 * @param minorUnit 记账与结算金额的小数位数，0 至 8
 * @param roundingMode 金额归整到币种精度的方式
 * @param status Platform 当前状态
 */
public record ReferenceCurrency(String code, String numericCode, int minorUnit, CurrencyRoundingMode roundingMode,
    ReferenceDataStatus status) implements ReferenceItem {
}
