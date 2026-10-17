package com.paytech.commerce.client.api.v1.order.response;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.OptBoolean;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 订单取消响应。
 * <li>200 返回原取消终态；202 只返回订单号、取消进度和待定原因。</li>
 * <li>不输出另一种结果的空字段或内部版本。</li>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CancelOrderResponseBody {
    /** 不透明、大小写敏感的 Commerce 订单号。 */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotBlank
    @Size(max = 64)
    private String orderNo;

    /** 取消后的订单状态；仅 200 返回，固定 CANCELLED。 */
    private String orderStatus;

    /** 取消后的会话状态；仅 200 且实际关联会话时返回 CANCELLED，无会话的 Direct Pay 不返回。 */
    private String checkoutStatus;

    /** 原始取消确认 UTC 时刻；仅 200 返回，重试不改写。 */
    private Instant cancelledAt;

    /** 取消进度；仅 202 返回，当前固定 PENDING。 */
    private String cancellationStatus;

    /** 待定原因；仅 202 返回，当前固定 PAYMENT_IN_PROGRESS。 */
    private String reasonCode;
}
