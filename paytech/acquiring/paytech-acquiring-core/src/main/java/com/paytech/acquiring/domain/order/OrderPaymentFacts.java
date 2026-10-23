package com.paytech.acquiring.domain.order;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Payment PaymentSucceeded 携带、acquiring 不持久化但 OrderPaymentSucceeded 需要转发的支付事实
 * <li>只在内存与 ORDER_PAYMENT_SUCCEEDED 补偿任务的 payloadJson 中出现；订单与冻结商业字段由发布时回读。</li>
 * <li>除已捕获金额、成功时间与成功尝试外均可为空：老报文缺字段或非卡支付时为 null，由消费方按自身合同处理。</li>
 */
@Getter
@AllArgsConstructor
public class OrderPaymentFacts {
    /** 成功的支付尝试 ID；发布时与订单支付投影的成功尝试核对。 */
    @Positive
    private final long paymentAttemptId;

    /** 已捕获金额，币种主单位。 */
    @NotNull
    private final BigDecimal capturedAmount;

    /** 支付成功的业务时间（UTC）。 */
    @NotNull
    private final Instant paymentSucceededTime;

    /** 支付方式目录代码。 */
    private final String paymentMethodCode;

    /** 支付渠道 ID，十进制字符串。 */
    private final String channelId;

    /** 渠道技术账户 ID，十进制字符串。 */
    private final String channelAccountId;

    /** MID ID，十进制字符串。 */
    private final String midId;

    /** 渠道外部交易号。 */
    private final String channelTransactionRef;

    /** 渠道交易引用内部 ID，十进制字符串。 */
    private final String channelTransactionRefId;

    /** 渠道交易引用类型代码。 */
    private final String channelReferenceType;

    /** 卡组织。 */
    private final String cardBrand;

    /** 卡资金类型。 */
    private final String fundingType;

    /** 卡等级。 */
    private final String cardLevel;
}
