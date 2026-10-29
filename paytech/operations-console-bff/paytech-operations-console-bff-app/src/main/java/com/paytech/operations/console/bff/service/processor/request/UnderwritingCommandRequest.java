package com.paytech.operations.console.bff.service.processor.request;

import java.util.function.Function;

import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.risk.decision.api.control.request.UnderwritingInputBody;
import com.paytech.risk.decision.api.control.response.UnderwritingResponseBody;

import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 承保写命令的内部请求
 * <li>clientId 与 underwritingId 至少一个非空：只有版本 ID 时先读取版本取得所属 Client。</li>
 * <li>withInput 为 true 时 BFF 按 Client 重新读取输入快照（strict），作为 command 的入参；复核不需要输入。</li>
 */
@Getter
@AllArgsConstructor
public class UnderwritingCommandRequest {
    /** 入站边界已校验的操作人身份。 */
    @NotNull(message = "操作人身份不能为空")
    private final OperatorContext operator;

    /** Client ID；按 Client 发起的命令非空。 */
    private final String clientId;

    /** 承保版本 ID；按版本发起的命令非空。 */
    private final String underwritingId;

    /** 是否需要重新读取输入快照。 */
    private final boolean withInput;

    /** 已绑定页面 Body 的 Risk 写命令（单次调用，不重试）；入参为输入快照，withInput 为 false 时为 null。 */
    @NotNull(message = "承保命令不能为空")
    private final Function<UnderwritingInputBody, UnderwritingResponseBody> command;
}
