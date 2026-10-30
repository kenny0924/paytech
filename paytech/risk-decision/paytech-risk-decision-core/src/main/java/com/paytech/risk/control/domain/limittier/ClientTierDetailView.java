package com.paytech.risk.control.domain.limittier;

import java.math.BigDecimal;
import java.util.List;

/**
 * Client 档位详情：查询结果加本档位按币种额度、已用与未配置币种
 *
 * @param tier 档位查询结果
 * @param quotas 本档位已配置币种的额度与已用，按币种排序；无档位为空集合
 * @param unconfiguredCurrencies 在其他档位配置过、本档位未配置的币种
 * @param allowsUnconfiguredCurrency 本档位未配置币种是否放行；无档位为空
 */
public record ClientTierDetailView(ClientTierView tier, List<QuotaUsage> quotas, List<String> unconfiguredCurrencies,
    Boolean allowsUnconfiguredCurrency) {

    /**
     * 一个币种的额度与已用
     *
     * @param currency 币种
     * @param single 单笔上限；未配置为空
     * @param day UTC 当日上限；未配置为空
     * @param last30Days 近 30 个 UTC 自然日上限；未配置为空
     * @param usedToday UTC 当日已用
     * @param usedLast30Days 近 30 个 UTC 自然日（含当日）已用
     */
    public record QuotaUsage(String currency, BigDecimal single, BigDecimal day, BigDecimal last30Days,
        BigDecimal usedToday, BigDecimal usedLast30Days) { }
}
