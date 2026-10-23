package com.paytech.billing.domain;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Builder;
import lombok.Getter;

/**
 * acquiring OrderPaymentSucceeded v1 信封中的订单冻结商业字段与支付成功事实（billing 本地模型，技术方案 4.3 S13）
 * <li>由 {@code OrderPaymentSucceededEventReader} 校验信封与 Payload 后一次构造，不可变；ID 已解析为数值、金额为精确十进制、时间为 UTC。</li>
 * <li>可空的支付字段：channelTransactionRef、channelTransactionRefId、channelReferenceType、cardBrand、fundingType、cardLevel。</li>
 */
@Getter
@Builder
public class OrderPaymentSucceededFact {
    /** 计费事实来源系统代码：事件发布方 acquiring。 */
    public static final String SOURCE_SYSTEM = "acquiring";

    /** 来源事件键前缀：同一支付尝试只登记一条 PAYMENT 事实。 */
    public static final String SOURCE_EVENT_KEY_PREFIX = "PAYMENT_ATTEMPT:";

    /** 信封事件 ID，只用于日志。 */
    private final String eventId;

    /**
     * 信封聚合版本（订单推进 PAID 后的版本）
     * <li>写入事实 source_version；不进入幂等键（S3）。</li>
     */
    private final long aggregateVersion;

    /** 信封 Payload 的 RFC 8785 SHA-256 小写十六进制，写入 source_payload_hash。 */
    private final String payloadHash;

    /** 订单与冻结商业字段 */
    private final OrderCommercialContext order;

    /** 支付意图主键 */
    private final long paymentIntentId;

    /** 成功的支付尝试主键 */
    private final long paymentAttemptId;

    /** 本次支付成功收取的金额，币种主单位；事实毛额取它 */
    private final BigDecimal capturedAmount;

    /** 支付成功时间（UTC），事实 business_time 取它 */
    private final Instant paymentSucceededTime;

    /** Payment 支付方式目录代码 */
    private final String paymentMethodCode;

    /** Payment Channel 主键 */
    private final long channelId;

    /** 渠道技术账户主键 */
    private final long channelAccountId;

    /** 渠道商户号主键 */
    private final long midId;

    /**
     * 渠道外部交易号
     * <li>可空；CHANNEL_TRANSACTION_REF 引用的 external_transaction_id。</li>
     */
    private final String channelTransactionRef;

    /**
     * Payment 渠道交易引用主键
     * <li>可空；CHANNEL_TRANSACTION_REF 引用的 ref_id。</li>
     */
    private final Long channelTransactionRefId;

    /**
     * 渠道交易引用类型（PAY 成功恒为 PAY）
     * <li>可空；CHANNEL_TRANSACTION_REF 引用的 external_reference_type。</li>
     */
    private final String channelReferenceType;

    /**
     * 卡组织代码
     * <li>非卡支付或未识别为空。</li>
     */
    private final String cardBrand;

    /**
     * 卡资金类型代码
     * <li>非卡支付或未识别为空。</li>
     */
    private final String fundingType;

    /**
     * 卡等级代码
     * <li>非卡支付或未识别为空。</li>
     */
    private final String cardLevel;

    /** 计费事实的来源事件键：{@code PAYMENT_ATTEMPT:<paymentAttemptId>}。 */
    public String sourceEventKey() {
        return SOURCE_EVENT_KEY_PREFIX + paymentAttemptId;
    }

    /** 渠道交易引用三项（引用 ID、引用类型、外部交易号）齐全（S4）。 */
    public boolean channelReferenceComplete() {
        return channelTransactionRefId != null && channelReferenceType != null && channelTransactionRef != null;
    }
}
