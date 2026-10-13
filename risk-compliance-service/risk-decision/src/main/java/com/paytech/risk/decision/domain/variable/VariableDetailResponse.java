package com.paytech.risk.decision.domain.variable;

import com.paytech.risk.decision.domain.variable.RiskVariable.*;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 跨模块详情响应，读取时刻只用于当前值展示。 */
@Getter
@AllArgsConstructor
public class VariableDetailResponse {
    /** 当前详情投影。 */
    private final View value;

    /** UTC 微秒读取时刻。 */
    private final Instant valuesAsOf;

}
