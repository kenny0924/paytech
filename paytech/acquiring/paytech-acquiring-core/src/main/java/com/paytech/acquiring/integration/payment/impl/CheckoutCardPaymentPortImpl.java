package com.paytech.acquiring.integration.payment.impl;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.paytech.acquiring.domain.checkout.CheckoutUpstreamProtocolException;
import com.paytech.acquiring.domain.customer.CheckoutSubmissionModels.CustomerAction;
import com.paytech.acquiring.domain.customer.CheckoutSubmissionModels.PaymentReference;
import com.paytech.acquiring.domain.customer.CheckoutSubmissionModels.PaymentRequest;
import com.paytech.acquiring.domain.customer.CheckoutSubmissionModels.PaymentResult;
import com.paytech.acquiring.domainservice.port.customer.CheckoutCardPaymentPort;
import com.paytech.acquiring.api.checkout.v1.enums.CustomerPaymentStatus;
import com.paytech.payment.api.execution.PaymentIntentApi;
import com.paytech.payment.api.execution.request.PayRequestBody;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;
import com.paytech.payment.api.execution.response.GetSubmissionResponseBody;
import com.paytech.payment.api.execution.response.PayResponseBody;

import lombok.AllArgsConstructor;

/**
 * 经 Payment 统一 pay 提交卡支付（Customer 与存卡支付技术方案 §1.3 G-01）：只适配 DTO，HTTP 与错误解码交给公共客户端。
 *
 * <p>
 * 以来源提交 ID 作为 Payment 原提交身份，Idempotency-Key 为 {@code submission:<sourceSubmissionId>}；本期只支持自动捕获。 Payment 的尝试状态映射为 Checkout
 * 支付状态：成功、失败、取消直接映射；处理中带 3DS 指令或待完成交互时为待消费者操作（动作标识使用尝试 ID），否则为处理中；其他值失败关闭。
 * 查询经 EX-I06 读取原提交，不返回 3DS 跳转地址。
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
     * 经 Payment EX-I06 按 Intent 与来源提交查询原支付结果
     * <li>Payment 返回 404（Intent 不属于该 Client、提交不存在或不属于该 Intent）时返回 null。</li>
     * <li>依赖不可用、其他 Problem 与解码异常由公共客户端原样抛出。</li>
     */
    @Override
    public PaymentResult query(long clientId, PaymentReference reference) {
        ResponseEntity<GetSubmissionResponseBody> response;
        try {
            response = intents.getSubmission(Long.toString(reference.intentId()), Long.toString(reference.submissionId()),
                Long.toString(clientId));
        } catch (FailException failure) {
            if (CommonErrorCode.NOT_FOUND.getCode().equals(failure.getCode())) {
                return null;
            }
            throw failure;
        }
        if (response == null || response.getBody() == null || response.getStatusCode().value() != 200) {
            throw new CheckoutUpstreamProtocolException("Payment 支付查询响应状态或内容无效");
        }
        return queried(reference, response.getBody());
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
        String status = status(body.getStatus(), action != null);
        return new PaymentResult(reference.submissionId(), reference.orderId(), reference.intentId(),
            body.getAttemptId(), status, null, action, null, now);
    }

    /** 查询结果映射：归属必须与本地坐标一致，交互只有类型与期限，失败代码原样透传。 */
    static PaymentResult queried(PaymentReference reference, GetSubmissionResponseBody body) {
        if (!Long.toString(reference.intentId()).equals(body.getIntentId())
            || !Long.toString(reference.submissionId()).equals(body.getSourceSubmissionId())) {
            throw new CheckoutUpstreamProtocolException("Payment 支付查询归属不一致");
        }
        var action = interaction(body);
        String status = status(body.getStatus(), action != null);
        return new PaymentResult(reference.submissionId(), reference.orderId(), reference.intentId(),
            body.getAttemptId(), status, null, action, failureCode(body.getFailureCode()),
            instant(body.getObservedAt()));
    }

    /** Payment 尝试状态映射为 Checkout 支付状态；处理中有待完成交互时为待消费者操作，其他值失败关闭。 */
    static String status(String attemptStatus, boolean actionRequired) {
        return switch (attemptStatus == null ? "" : attemptStatus) {
            case "SUCCEEDED" -> CustomerPaymentStatus.SUCCEEDED.getCode();
            case "FAILED" -> CustomerPaymentStatus.FAILED.getCode();
            case "CANCELLED" -> CustomerPaymentStatus.CANCELLED.getCode();
            case "PROCESSING" -> actionRequired ? CustomerPaymentStatus.REQUIRES_ACTION.getCode()
                : CustomerPaymentStatus.PROCESSING.getCode();
            default -> throw new CheckoutUpstreamProtocolException("Payment 支付状态无效");
        };
    }

    /** 查询的交互：动作标识使用尝试 ID，无跳转地址。 */
    static CustomerAction interaction(GetSubmissionResponseBody body) {
        var interaction = body.getInteraction();
        if (interaction == null) {
            return null;
        }
        return new CustomerAction(body.getAttemptId(), interaction.getType(),
            interaction.getExpiresAt() == null ? null : instant(interaction.getExpiresAt()), null);
    }

    /** 必填 RFC 3339 时间；缺失或格式错误视为上游协议错误。 */
    static Instant instant(String value) {
        if (value == null) {
            throw new CheckoutUpstreamProtocolException("Payment 支付查询时间缺失");
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeException invalidTime) {
            throw new CheckoutUpstreamProtocolException("Payment 支付查询时间无效", invalidTime);
        }
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

    /** 对外失败码：渠道码不符合公开格式（如纯数字）时统一为 CHANNEL_DECLINED，不把渠道原码当成协议错误。 */
    static String failureCode(String channelCode) {
        if (channelCode == null || channelCode.matches("[A-Z][A-Z0-9_]{0,63}")) {
            return channelCode;
        }
        return "CHANNEL_DECLINED";
    }
}
