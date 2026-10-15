package com.paytech.commerce.integration.payment.impl;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;

import org.springframework.stereotype.Component;

import com.paytech.commerce.domain.checkout.CheckoutUpstreamProtocolException;
import com.paytech.commerce.domain.customer.CheckoutSubmissionModels.CustomerAction;
import com.paytech.commerce.domain.customer.CheckoutSubmissionModels.PaymentReference;
import com.paytech.commerce.domain.customer.CheckoutSubmissionModels.PaymentRequest;
import com.paytech.commerce.domain.customer.CheckoutSubmissionModels.PaymentResult;
import com.paytech.commerce.domainservice.port.customer.CheckoutCardPaymentPort;
import com.paytech.commerce.customer.api.v1.enums.CustomerPaymentStatus;
import com.paytech.payment.api.execution.PaymentIntentApi;
import com.paytech.payment.api.execution.request.PayRequestBody;
import com.paytech.payment.api.execution.response.PayResponseBody;

import lombok.AllArgsConstructor;

/**
 * 经 Payment 统一 pay 提交卡支付（Customer 与存卡支付技术方案 §1.3 G-01）：只适配 DTO，HTTP 与错误解码交给公共客户端。
 *
 * <p>
 * 以来源提交 ID 作为 Payment 原提交身份，Idempotency-Key 为 {@code submission:<sourceSubmissionId>}；本期只支持自动捕获。 Payment 的尝试状态映射为 Checkout
 * 支付状态：成功、失败、取消直接映射；处理中带 3DS 指令时为待消费者操作（动作标识使用尝试 ID），否则为处理中；其他值失败关闭。
 */
@Component
@AllArgsConstructor
public class CheckoutCardPaymentPortImpl implements CheckoutCardPaymentPort {
    /** 本期只支持自动捕获；手动捕获的 AUTHORIZE 在 Payment 尚未实现。 */
    static final String CAPTURE_MODE = "AUTOMATIC";

    private final Clock clock;
    private final PaymentIntentApi intents;

    /** 组装 pay 请求并映射尝试状态。 */
    @Override
    public PaymentResult submit(PaymentRequest request) {
        var reference = request.receipt().reference();
        var response = intents.pay(Long.toString(reference.intentId()), "submission:" + reference.submissionId(),
            body(request));
        if (response == null || response.getBody() == null
            || (response.getStatusCode().value() != 200 && response.getStatusCode().value() != 202)) {
            throw new CheckoutUpstreamProtocolException("Payment 支付响应状态或内容无效");
        }
        return result(reference, response.getBody(), clock.instant());
    }

    /**
     * TODO by CLAUDE: Payment 尚无按来源提交查询原支付结果的接口；接入前保持原占位行为，固定返回处理中、不推断成功或失败。
     */
    @Override
    public PaymentResult query(PaymentReference reference) {
        return new PaymentResult(reference.submissionId(), reference.orderId(), reference.intentId(), null,
            CustomerPaymentStatus.PROCESSING.getCode(), null, null, null, clock.instant());
    }

    /** pay 请求：工具 Token、自动捕获、回跳地址与付款人信息。 */
    static PayRequestBody body(PaymentRequest request) {
        var payMethod = new PayRequestBody.PayMethod();
        payMethod.setPaymentMethodCode(request.instrument().paymentMethodCode());
        payMethod.setPaymentInstrumentToken(request.instrument().token());
        var body = new PayRequestBody();
        body.setClientId(Long.toString(request.clientId()));
        body.setSourceSubmissionId(Long.toString(request.receipt().submissionId()));
        body.setCaptureMode(CAPTURE_MODE);
        body.setPayMethod(payMethod);
        body.setReturnUrl(request.payer().returnUrl());
        body.setConsumerReference(request.payer().consumerReference());
        body.setConsumerIpAddress(request.payer().clientIp());
        body.setConsumerUserAgent(request.payer().userAgent());
        return body;
    }

    /** 尝试状态映射为 Checkout 支付状态。 */
    static PaymentResult result(PaymentReference reference, PayResponseBody body, Instant now) {
        var action = action(body);
        String status = switch (body.getStatus() == null ? "" : body.getStatus()) {
            case "SUCCEEDED" -> CustomerPaymentStatus.SUCCEEDED.getCode();
            case "FAILED" -> CustomerPaymentStatus.FAILED.getCode();
            case "CANCELLED" -> CustomerPaymentStatus.CANCELLED.getCode();
            case "PROCESSING" -> action == null ? CustomerPaymentStatus.PROCESSING.getCode()
                : CustomerPaymentStatus.REQUIRES_ACTION.getCode();
            default -> throw new CheckoutUpstreamProtocolException("Payment 支付状态无效");
        };
        return new PaymentResult(reference.submissionId(), reference.orderId(), reference.intentId(),
            body.getAttemptId(), status, null, action, null, now);
    }

    /** 3DS 指令：动作标识使用尝试 ID；其他交互类型失败关闭。 */
    static CustomerAction action(PayResponseBody body) {
        var next = body.getNextAction();
        if (next == null) {
            return null;
        }
        try {
            return new CustomerAction(body.getAttemptId(), next.getType(), next.getExpiresAt() == null ? null
                : Instant.parse(next.getExpiresAt()), next.getRedirectUrl());
        } catch (DateTimeException invalidTime) {
            throw new CheckoutUpstreamProtocolException("Payment 交互期限无效", invalidTime);
        }
    }
}
