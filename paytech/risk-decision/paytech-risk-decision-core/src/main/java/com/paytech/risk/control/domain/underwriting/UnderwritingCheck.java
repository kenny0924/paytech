package com.paytech.risk.control.domain.underwriting;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 自动承保判定的一项检查结果。 */
@Getter
@AllArgsConstructor
public final class UnderwritingCheck {
    /** 检查项。 */
    private final UnderwritingCheckCode code;

    /** 是否通过。 */
    private final boolean passed;
}
