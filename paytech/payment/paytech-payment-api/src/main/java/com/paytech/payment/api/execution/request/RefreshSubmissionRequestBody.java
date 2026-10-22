package com.paytech.payment.api.execution.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** 刷新来源提交支付结果的请求 */
@Getter
@Setter
public class RefreshSubmissionRequestBody {
    /**
     * Client ID
     * <li>正十进制 long 字符串，无前导零；必须与 Intent 归属一致。</li>
     */
    @NotBlank(message = "Client ID 必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "Client ID 必须为无前导零的正整数字符串")
    @DecimalMax(value = "9223372036854775807", message = "Client ID 超出有效范围")
    private String clientId;
}
