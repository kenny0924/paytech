package com.paytech.operations.console.bff.service.processor.request;

import java.util.function.Supplier;

import com.paytech.risk.decision.api.control.response.ClientTierChangeResponseBody;

import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 撤回、审批或驳回档位变更申请（L5—L9）的内部请求：已绑定页面 Body 的单次 Risk 写命令。 */
@Getter
@AllArgsConstructor
public class LimitTierChangeCommandRequest {
    /** 已绑定操作人、申请 ID 与页面 Body 的 Risk 写命令（单次调用，不重试）。 */
    @NotNull(message = "档位命令不能为空")
    private final Supplier<ClientTierChangeResponseBody> command;
}
