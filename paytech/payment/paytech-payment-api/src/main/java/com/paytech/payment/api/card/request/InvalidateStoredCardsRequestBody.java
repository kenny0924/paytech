package com.paytech.payment.api.card.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** 按 Customer 失效已存卡请求 */
@Getter
@Setter
public class InvalidateStoredCardsRequestBody {
    /** Client ID */
    @NotBlank(message = "Client ID 必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "Client ID 必须为无前导零的正整数")
    @DecimalMax(value = "9223372036854775807", message = "Client ID 超出有效范围")
    private String clientId;
}
