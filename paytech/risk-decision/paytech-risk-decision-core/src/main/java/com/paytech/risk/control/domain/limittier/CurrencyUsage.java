package com.paytech.risk.control.domain.limittier;

import java.math.BigDecimal;

/**
 * 一个币种的放行累计（不换汇、分币种累计，按 UTC 自然日汇总）
 *
 * @param currency ISO 4217 币种
 * @param usedToday UTC 当日放行金额
 * @param usedLast30Days 近 30 个 UTC 自然日（含当日）放行金额
 */
public record CurrencyUsage(String currency, BigDecimal usedToday, BigDecimal usedLast30Days) { }
