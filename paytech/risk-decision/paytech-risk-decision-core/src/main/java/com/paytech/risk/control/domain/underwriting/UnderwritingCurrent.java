package com.paytech.risk.control.domain.underwriting;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 某 Client 当前的承保：有效结论、进行中版本与最新版本，均可为空。 */
@Getter
@AllArgsConstructor
public final class UnderwritingCurrent {
    /** 有效的 APPROVED 版本。 */
    private final UnderwritingView effective;

    /** 进行中的 DRAFT 或 SUBMITTED 版本。 */
    private final UnderwritingView open;

    /** 版本号最大的版本。 */
    private final UnderwritingView latest;
}
