package com.paytech.acquiring.integration.payment;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Commerce 订单终止 Payment 支付意图的出站端口，对应 Payment PAY-I12 终止接口。
 *
 * <p>
 * 只终止已有 Intent，绝不创建或补绑定；订单已保存 Intent ID 时直接终止，否则先按 {@code COMMERCE_ORDER + orderId} 定位。
 */
public interface PaymentTerminationClient {
    /**
     * 终止订单对应的支付意图
     *
     * @param request 订单身份、可选 Intent ID 与终止原因
     * @return 原订单的终止决定
     *         <li>Intent 不存在（按来源查询或终止返回 404）抛出 NOT_FOUND 的 FailException。</li>
     *         <li>其他 Problem 原样抛出；网络与依赖不可用由公共客户端抛出 ServiceUnavailableException。</li>
     *         <li>响应归属不一致或决定未知抛出 CheckoutUpstreamProtocolException。</li>
     */
    Result terminate(Request request);

    /**
     * 终止请求
     *
     * @param clientId 订单归属 Client
     * @param orderId Commerce 订单内部 ID，同时是 Payment 来源业务 ID
     * @param paymentIntentId 支付投影中的 Intent ID；创建响应丢失、投影未写入时为空
     * @param reason 终止原因，决定 Payment 安全终止后的 Intent 终态
     */
    record Request(long clientId, long orderId, Long paymentIntentId, TerminationReason reason) {
    }

    /**
     * 终止结果，只返回原订单的终止决定，不在终止链路补写支付投影。
     *
     * @param orderId 请求中的订单 ID
     * @param status 终止决定
     */
    record Result(long orderId, Status status) {
    }

    /** 终止决定。 */
    @Getter
    @RequiredArgsConstructor
    enum Status implements CodeEnum {
        /** 本次终止或此前已取消、过期，订单可以本地收尾。 */
        TERMINATED("TERMINATED"),

        /** 存在未决尝试，订单保持开放等待收敛。 */
        PENDING("PENDING"),

        /** 已有未释放授权，需要撤销。 */
        REVERSAL_REQUIRED("REVERSAL_REQUIRED"),

        /** Payment 已收款，资金退回须走退款。 */
        ALREADY_PAID("ALREADY_PAID");

        private final String code;
    }

    /** 终止原因，Code 与 Payment 终止请求体 reason 一致。 */
    @Getter
    @RequiredArgsConstructor
    enum TerminationReason implements CodeEnum {
        /** Client 取消订单，Payment 终态 CANCELLED。 */
        ORDER_CANCELLED("ORDER_CANCELLED"),

        /** 订单到期，Payment 终态 EXPIRED。 */
        ORDER_EXPIRED("ORDER_EXPIRED");

        private final String code;
    }
}
