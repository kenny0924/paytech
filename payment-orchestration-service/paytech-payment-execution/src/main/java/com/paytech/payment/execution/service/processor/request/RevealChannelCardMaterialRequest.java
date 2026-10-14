package com.paytech.payment.execution.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/** 渠道执行读卡请求 */
@Getter
@Setter
public class RevealChannelCardMaterialRequest {
    /** 调用方服务名，只接受 platform-support-service */
    @NotBlank(message = "调用方必填")
    private String caller;

    /** 支付工具 Token */
    @NotBlank(message = "支付工具 Token 必填")
    @Pattern(regexp = "[A-Za-z0-9_-]{1,64}", message = "支付工具 Token 格式无效")
    private String paymentInstrumentToken;

    /** 支付尝试 ID */
    @NotNull(message = "支付尝试 ID 必填")
    @Positive(message = "支付尝试 ID 必须为正整数")
    private Long paymentAttemptId;

    /** 读取用途，只接受 CHANNEL_EXECUTION */
    @NotBlank(message = "读取用途必填")
    @Pattern(regexp = "CHANNEL_EXECUTION", message = "读取用途只能为 CHANNEL_EXECUTION")
    private String purpose;

    @Override
    public String toString() {
        return "RevealChannelCardMaterialRequest[REDACTED]";
    }
}
