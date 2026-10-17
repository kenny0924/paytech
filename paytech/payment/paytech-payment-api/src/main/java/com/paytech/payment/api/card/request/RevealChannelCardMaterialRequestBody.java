package com.paytech.payment.api.card.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** 渠道执行读卡请求 */
@Getter
@Setter
public class RevealChannelCardMaterialRequestBody {
    /** 支付工具 Token */
    @NotBlank(message = "支付工具 Token 必填")
    @Pattern(regexp = "[A-Za-z0-9_-]{1,64}", message = "支付工具 Token 格式无效")
    private String paymentInstrumentToken;

    /** 支付尝试 ID，即渠道订单号 */
    @NotBlank(message = "支付尝试 ID 必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "支付尝试 ID 必须为无前导零的正整数")
    @DecimalMax(value = "9223372036854775807", message = "支付尝试 ID 超出有效范围")
    private String paymentAttemptId;

    /**
     * 读取用途
     * <li>CHANNEL_EXECUTION：渠道资金动作执行。</li>
     */
    @NotBlank(message = "读取用途必填")
    @Pattern(regexp = "CHANNEL_EXECUTION", message = "读取用途只能为 CHANNEL_EXECUTION")
    private String purpose;

    @Override
    public String toString() {
        return "RevealChannelCardMaterialRequestBody[REDACTED]";
    }
}
