package com.paytech.acquiring.service.processor.order.request;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Payment PaymentSucceeded 事件解析后的内部请求；只保留 Commerce 落地与核对所需的字段。 */
@Getter
@AllArgsConstructor
public class ApplyPaymentSucceededRequest {
    /** 事件幂等键，格式为 {@code PAYMENT:PAYMENT_ATTEMPT:{attemptId}:{attemptVersion}:PaymentSucceeded}。 */
    @NotBlank
    @Size(max = 128)
    private final String eventKey;

    /** 载荷 SHA-256，64 位小写十六进制，不含算法前缀。 */
    @NotBlank
    @Pattern(regexp = "[0-9a-f]{64}")
    private final String payloadHash;

    /** 支付尝试来源版本。 */
    @Positive
    private final long sourceVersion;

    /** 事件发生时间。 */
    @NotNull
    private final Instant occurredAt;

    /** 订单所属 Client。 */
    @Positive
    private final long clientId;

    /** Commerce 订单 ID（Intent 的来源 ID）。 */
    @Positive
    private final long orderId;

    /** Payment 支付意图 ID。 */
    @Positive
    private final long paymentIntentId;

    /** 成功的支付尝试 ID。 */
    @Positive
    private final long paymentAttemptId;

    /** Intent 冻结的经营主体编号。 */
    @NotBlank
    private final String entityNo;

    /** Intent 应付金额，币种主单位。 */
    @NotNull
    private final BigDecimal amount;

    /** ISO 4217 币种。 */
    @NotBlank
    private final String currency;

    /** 已捕获金额，币种主单位。 */
    @NotNull
    private final BigDecimal capturedAmount;

    /** 支付成功的业务时间。 */
    @NotNull
    private final Instant paymentSucceededTime;
}
