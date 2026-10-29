package com.paytech.risk.decision.api.control.response;

import lombok.Getter;
import lombok.Setter;

/** 估算敞口（参考值，不做门禁）：预计月流水 × 风险天数 ÷ 30。 */
@Getter
@Setter
public class UnderwritingExposureBody {

    /** 敞口金额，币种主单位十进制字符串，保留 2 位小数。 */
    private String amount;

    /** 敞口币种，等于预计月流水币种。 */
    private String currency;

    /**
     * 风险天数
     * <li>PRE_ORDER 为交付天数 + T+N；INSTANT、SERVICE 为 T+N；SUBSCRIPTION 为 30 + T+N。</li>
     */
    private Integer riskDays;

    /** 结算周期建议未填、按 0 计算时为 true。 */
    private Boolean settlementDaysAssumed;

    /** 计算式文本，例如 "50000.00 × 15 ÷ 30"；页面可直接展示。 */
    private String formula;
}
