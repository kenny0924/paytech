package com.paytech.risk.decision.api.control.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 一个档位的按币种额度。 */
@Getter
@Setter
public class TierQuotaTierBody {

    /** 档位。 */
    private String tier;

    /** 已配置的币种额度，按币种代码排序。 */
    private List<TierQuotaCurrencyBody> currencies;

    /** 未配置币种的处理：STANDARD 为 ALLOW（不限），其余为 DENY（拒绝）。 */
    private String unconfiguredCurrencyPolicy;
}
