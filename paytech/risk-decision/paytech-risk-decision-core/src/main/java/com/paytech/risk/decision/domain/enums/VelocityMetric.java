package com.paytech.risk.decision.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 实时累计算法。 */
public enum VelocityMetric implements CodeEnum {
    /** 窗口内事件数。 */
    COUNT("COUNT"),

    /** 窗口内与本次同币种事件的金额合计。 */
    SUM_AMOUNT("SUM_AMOUNT"),

    /** 窗口内关联的不同去重主体数。 */
    DISTINCT_COUNT("DISTINCT_COUNT"),

    /** 窗口内最近一次事件的金额。 */
    LAST_AMOUNT("LAST_AMOUNT"),

    /** 距窗口内最近一次事件的秒数。 */
    SECONDS_SINCE_LAST("SECONDS_SINCE_LAST"),

    /**
     * 按 UTC 自然日汇总的、与本次同币种的放行金额（REQ-04 日 / 月额度）
     * <li>只用于 CLIENT 主体与放行事件；窗口 86400 表示 UTC 当日，2592000 表示近 30 个 UTC 自然日（含当日）。</li>
     * <li>读取日汇总表，不扫描事件明细。</li>
     */
    DAILY_SUM_AMOUNT("DAILY_SUM_AMOUNT");

    private final String code;

    VelocityMetric(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
