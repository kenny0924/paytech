package com.paytech.payment.api.execution.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** 终止支付意图请求 */
@Getter
@Setter
public class TerminateIntentRequestBody {
    /**
     * Client ID
     * <li>正十进制 long 字符串，无前导零；必须与 Intent 归属一致。</li>
     */
    @NotBlank(message = "Client ID 必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "Client ID 必须为无前导零的正整数字符串")
    @DecimalMax(value = "9223372036854775807", message = "Client ID 超出有效范围")
    private String clientId;

    /**
     * 终止原因
     * <li>ORDER_CANCELLED：取消订单；OPERATING_ENTITY_CLOSED：经营主体关闭；ORDER_EXPIRED：订单到期。</li>
     * <li>安全终止时前两者写入 CANCELLED，ORDER_EXPIRED 写入 EXPIRED；不影响终止决定。</li>
     * <li>三种原因均须满足安全终止条件，不触发撤销或退款。</li>
     */
    @NotBlank(message = "终止原因必填")
    @Pattern(regexp = "ORDER_CANCELLED|OPERATING_ENTITY_CLOSED|ORDER_EXPIRED", message = "终止原因无效")
    private String reason;
}
