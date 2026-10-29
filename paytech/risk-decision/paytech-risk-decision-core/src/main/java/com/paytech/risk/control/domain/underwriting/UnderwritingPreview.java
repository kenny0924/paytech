package com.paytech.risk.control.domain.underwriting;

import com.paytech.risk.control.core.enums.UnderwritingRiskLevel;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 自动承保判定与估算敞口预览（纯计算结果）。 */
@Getter
@AllArgsConstructor
public final class UnderwritingPreview {
    /** 自动承保判定。 */
    private final UnderwritingAutoRule autoRule;

    /** 估算敞口。 */
    private final ExposureEstimate exposure;

    /** 复核级别。 */
    private final UnderwritingRiskLevel reviewLevel;
}
