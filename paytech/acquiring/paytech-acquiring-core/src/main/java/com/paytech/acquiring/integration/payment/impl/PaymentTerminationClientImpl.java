package com.paytech.acquiring.integration.payment.impl;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.paytech.acquiring.domain.checkout.CheckoutUpstreamProtocolException;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.IntentRequest;
import com.paytech.acquiring.integration.payment.PaymentTerminationClient;
import com.paytech.payment.api.execution.PaymentIntentApi;
import com.paytech.payment.api.execution.request.TerminateIntentRequestBody;
import com.paytech.payment.api.execution.response.GetIntentResponseBody;

import lombok.AllArgsConstructor;

/**
 * 经 Payment PAY-I12 终止订单的支付意图：只适配 DTO，HTTP、Request ID 与错误解码交给公共客户端。
 *
 * <p>
 * 按来源查询或终止返回 404 时，公共客户端解码为 NOT_FOUND 的 FailException，原样抛给调用方表示 Intent 不存在；
 * 跨 Client 等 400 及其他 Problem 同样原样抛出，调用方不得当作不存在。
 */
@Component
@AllArgsConstructor
public class PaymentTerminationClientImpl implements PaymentTerminationClient {
    private final PaymentIntentApi intents;

    /**
     * 投影无 Intent ID 时先按来源定位，再按原因终止并映射决定
     * <li>只发起查询与终止，不创建 Intent；调用方须在本地事务外调用。</li>
     * <li>终止响应的 Intent 与请求不一致时抛出上游协议错误。</li>
     */
    @Override
    public Result terminate(Request request) {
        long intentId = request.paymentIntentId() != null ? request.paymentIntentId() : findIntentId(request);
        var body = new TerminateIntentRequestBody();
        body.setClientId(Long.toString(request.clientId()));
        body.setReason(request.reason().getCode());
        var response = responseBody(intents.terminateIntent(Long.toString(intentId), body));
        if (!Long.toString(intentId).equals(response.getIntentId())) {
            throw new CheckoutUpstreamProtocolException("Payment 终止响应归属不一致");
        }
        return new Result(request.orderId(), status(response.getDecision()));
    }

    /** 按 COMMERCE_ORDER + orderId 查询原 Intent；来源归属与 ID 格式不符视为上游协议错误。 */
    long findIntentId(Request request) {
        GetIntentResponseBody intent = responseBody(intents.getIntentBySource(
            IntentRequest.COMMERCE_ORDER_SOURCE_TYPE, Long.toString(request.orderId()),
            Long.toString(request.clientId())));
        if (!IntentRequest.COMMERCE_ORDER_SOURCE_TYPE.equals(intent.getSourceType())
            || !Long.toString(request.orderId()).equals(intent.getSourceId())
            || intent.getIntentId() == null || !intent.getIntentId().matches("[1-9][0-9]{0,18}")) {
            throw new CheckoutUpstreamProtocolException("Payment 按来源查询的 Intent 归属不一致");
        }
        try {
            return Long.parseLong(intent.getIntentId());
        } catch (NumberFormatException invalidId) {
            throw new CheckoutUpstreamProtocolException("Payment Intent 标识超出范围", invalidId);
        }
    }

    /** Payment 两个接口均约定 200 与非空响应体，其他成功状态视为上游协议错误。 */
    static <T> T responseBody(ResponseEntity<T> response) {
        if (response == null || response.getBody() == null || response.getStatusCode().value() != 200) {
            throw new CheckoutUpstreamProtocolException("Payment 终止链路响应状态或内容无效");
        }
        return response.getBody();
    }

    /** Payment 终止决定映射：已终止与此前终止均可本地收尾，其他同名，未知值失败关闭。 */
    static Status status(String decision) {
        return switch (decision == null ? "" : decision) {
            case "TERMINATED", "ALREADY_TERMINATED" -> Status.TERMINATED;
            case "PENDING" -> Status.PENDING;
            case "REVERSAL_REQUIRED" -> Status.REVERSAL_REQUIRED;
            case "ALREADY_PAID" -> Status.ALREADY_PAID;
            default -> throw new CheckoutUpstreamProtocolException("Payment 终止决定无效");
        };
    }
}
