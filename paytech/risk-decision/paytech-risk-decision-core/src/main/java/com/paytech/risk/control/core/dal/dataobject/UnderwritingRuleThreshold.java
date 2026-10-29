package com.paytech.risk.control.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Data;

/** 自动承保规则版本的币种月流水阈值（t_underwriting_rule_threshold）。 */
@Data
public class UnderwritingRuleThreshold {
    /** 阈值行 ID。 */
    private long thresholdId;

    /** 所属规则版本 ID。 */
    private long ruleVersionId;

    /** 预计月流水币种（ISO 4217）。 */
    private String currency;

    /** 可自动承保的预计月流水上限（含）。 */
    private BigDecimal monthlyVolumeLimit;

    /** 创建 UTC 时间。 */
    private Instant createTime;

    /** 更新 UTC 时间。 */
    private Instant updateTime;
}
