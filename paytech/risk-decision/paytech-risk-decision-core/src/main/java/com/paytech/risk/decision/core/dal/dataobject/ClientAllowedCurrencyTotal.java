package com.paytech.risk.decision.core.dal.dataobject;

import java.math.BigDecimal;

import lombok.Data;

/** 按币种合计的 Client 放行金额（t_client_allowed_daily_total 只读查询结果）。 */
@Data
public class ClientAllowedCurrencyTotal {
    /** ISO 4217 币种。 */
    private String currency;

    /** UTC 当日合计。 */
    private BigDecimal amountToday;

    /** 近 30 个 UTC 自然日（含当日）合计。 */
    private BigDecimal amountLast30Days;
}
