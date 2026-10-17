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
    SECONDS_SINCE_LAST("SECONDS_SINCE_LAST");

    private final String code;

    VelocityMetric(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
