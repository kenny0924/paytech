package com.paytech.card.api.ticket.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** 签发读卡票据请求 */
@Getter
@Setter
public class IssueCardMaterialTicketRequestBody {
    /** Card 工具引用 */
    @NotBlank(message = "工具引用必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "工具引用无效")
    @DecimalMax(value = "9223372036854775807", message = "工具引用超出有效范围")
    private String instrumentRef;

    /** 支付尝试 ID（渠道订单号），只用于审计 */
    @NotBlank(message = "支付尝试 ID 必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "支付尝试 ID 必须为无前导零的正整数")
    @DecimalMax(value = "9223372036854775807", message = "支付尝试 ID 超出有效范围")
    private String paymentAttemptId;

    /** 当前资金动作 ID，只用于审计 */
    @NotBlank(message = "支付动作 ID 必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "支付动作 ID 必须为无前导零的正整数")
    @DecimalMax(value = "9223372036854775807", message = "支付动作 ID 超出有效范围")
    private String paymentActionId;

    /**
     * 用途
     * <li>CHANNEL_EXECUTION：渠道资金动作执行。</li>
     */
    @NotBlank(message = "用途必填")
    @Pattern(regexp = "CHANNEL_EXECUTION", message = "用途只能为 CHANNEL_EXECUTION")
    private String purpose;

    /** 票据有效秒数，1–90；不超过工具剩余期限 */
    @NotNull(message = "票据有效秒数必填")
    @Min(value = 1, message = "票据有效秒数至少 1")
    @Max(value = 90, message = "票据有效秒数最多 90")
    private Integer ttlSeconds;
}
