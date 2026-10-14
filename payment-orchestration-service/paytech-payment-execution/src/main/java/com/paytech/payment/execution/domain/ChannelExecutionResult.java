package com.paytech.payment.execution.domain;

import java.util.Map;

import com.paytech.payment.execution.core.enums.ChannelExecutionOutcome;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 渠道动作结论与标准 API 输出；输出可能含临时交互指令，不得持久化。 */
@Getter
@AllArgsConstructor
public class ChannelExecutionResult {
    /** 执行结论 */
    private final ChannelExecutionOutcome outcome;

    /** 标准 API 输出；ERROR、NOT_SENT 时为空映射 */
    private final Map<String, Object> outputs;

    /** NOT_SENT 的原因代码，其余为 null */
    private final String failureCode;
}
