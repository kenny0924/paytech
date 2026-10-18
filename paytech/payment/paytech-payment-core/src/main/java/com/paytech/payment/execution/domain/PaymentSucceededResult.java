package com.paytech.payment.execution.domain;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Builder;
import lombok.Getter;

/**
 * 已成功收款的支付尝试；PaymentSucceeded 事件的业务事实来源
 * <li>由补偿 Handler 从 Attempt、Intent、支付工具 Token、成功 PAY 动作及其渠道交易引用重建，不含卡数据、付款人引用与网络信息。</li>
 */
@Getter
@Builder
public class PaymentSucceededResult {
    /** 支付尝试 ID，作为事件来源 ID */
    private final long paymentAttemptId;

    /** 支付尝试版本，作为事件来源版本 */
    private final long attemptVersion;

    /** Client ID */
    private final long clientId;

    /** 来源业务类型 */
    private final String sourceType;

    /** 来源业务 ID（Commerce 订单 ID） */
    private final long sourceId;

    /** 支付意图 ID */
    private final long paymentIntentId;

    /** 来源提交 ID */
    private final long sourceSubmissionId;

    /** 经营主体编号 */
    private final String entityNo;

    /** 支付意图金额 */
    private final BigDecimal amount;

    /** 币种 */
    private final String currency;

    /** 已捕获金额 */
    private final BigDecimal capturedAmount;

    /** 收款成功时间：渠道结果时间，缺失时取尝试更新时间 */
    private final Instant succeededAt;

    /** 精确支付方式代码 */
    private final String paymentMethodCode;

    /** 支付渠道 ID */
    private final long channelId;

    /** 渠道账号 ID */
    private final long channelAccountId;

    /** 渠道 MID */
    private final long midId;

    /** 渠道返回的真实交易编号 */
    private final String channelTransactionRef;

    /** 卡支付时的 Card 工具引用；非卡支付为空（Schema v2，供 card 存卡） */
    private final String instrumentRef;

    /** 消费者是否同意支付成功后保存卡（Schema v2） */
    private final boolean consentToSave;

    /** 本次支付关联的 Commerce Customer；访客支付为空（Schema v2） */
    private final Long customerId;
}
