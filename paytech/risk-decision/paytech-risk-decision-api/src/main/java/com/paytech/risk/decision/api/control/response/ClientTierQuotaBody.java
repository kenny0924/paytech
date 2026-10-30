package com.paytech.risk.decision.api.control.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 一个币种的档位额度与已用；金额为币种主单位十进制字符串
 * <li>已用 = 该 Client 该币种风控放行的支付尝试金额，按 UTC 自然日汇总，含之后支付失败的。</li>
 */
@Getter
@Setter
public class ClientTierQuotaBody {

    /** ISO 4217 币种。 */
    private String currency;

    /** 单笔上限；未配置为空。 */
    private String single;

    /** UTC 当日上限；未配置为空。 */
    private String day;

    /** 近 30 个 UTC 自然日（含当日）上限；未配置为空。 */
    private String last30Days;

    /** UTC 当日已用。 */
    private String usedToday;

    /** 近 30 个 UTC 自然日（含当日）已用。 */
    private String usedLast30Days;
}
