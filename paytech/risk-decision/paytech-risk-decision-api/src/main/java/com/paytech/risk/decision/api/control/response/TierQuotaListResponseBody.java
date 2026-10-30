package com.paytech.risk.decision.api.control.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 四档 × 币种额度（L10），由风控常量按命名约定解析
 * <li>常量 TIER_{TIER}_{CCY}_{SINGLE|DAY|MONTH}（MONEY，固定币种）与集合 TIER_{TIER}_CURRENCIES；改常量下一次评估即生效。</li>
 */
@Getter
@Setter
public class TierQuotaListResponseBody {

    /** 按 VERIFICATION、OBSERVATION、STANDARD、RESTRICTED 顺序的四个档位。 */
    private List<TierQuotaTierBody> tiers;

    /** 命名不规范、缺项、币种集合与常量不一致等问题，供页面提示；没有为空集合。 */
    private List<String> configurationIssues;
}
