package com.paytech.risk.decision.api.control.response;

import lombok.Getter;
import lombok.Setter;

/** 一个档位一个币种的额度；金额为币种主单位十进制字符串，未配置为空。 */
@Getter
@Setter
public class TierQuotaCurrencyBody {

    /** ISO 4217 币种。 */
    private String currency;

    /** 单笔上限。 */
    private String single;

    /** UTC 当日上限。 */
    private String day;

    /** 近 30 个 UTC 自然日（含当日）上限。 */
    private String last30Days;
}
