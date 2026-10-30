package com.paytech.tax.domain.calculation;

import java.math.BigDecimal;

/**
 * 按辖区 + 注册 + 纳税主体汇总的应收税额（与 OrderPaymentSucceeded.taxComponents 一一对应）
 *
 * @param jurisdictionCode 征税辖区
 * @param taxType 税种
 * @param registrationId 注册 ID
 * @param taxSubjectPartyId 纳税主体 Party ID
 * @param taxAmount 汇总税额，大于 0
 */
public record TaxComponentSummary(String jurisdictionCode, String taxType, Long registrationId, long taxSubjectPartyId,
    BigDecimal taxAmount) {
}
