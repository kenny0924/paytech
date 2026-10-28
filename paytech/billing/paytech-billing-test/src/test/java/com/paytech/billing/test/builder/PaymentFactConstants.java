package com.paytech.billing.test.builder;

import java.time.Instant;

import com.paytech.commercial.api.constant.CommercialCodes;

/**
 * 自动 PAYMENT 事实场景的稳定测试标识：OrderPaymentSucceeded 信封与四个上游响应 DTO 的默认值彼此一致
 * <li>业务时间 2026-10-21（周三）08:00Z，DEFAULT 日历、UTC、截止 86399、T+1 → 2026-10-22T23:59:59Z。</li>
 */
public final class PaymentFactConstants {
    /** CCT Client。 */
    public static final long CLIENT_ID = 31001L;

    /** 收单订单。 */
    public static final long ORDER_ID = 41001L;

    /** 支付意图。 */
    public static final long PAYMENT_INTENT_ID = 42001L;

    /** 支付尝试（信封聚合 ID）。 */
    public static final long PAYMENT_ATTEMPT_ID = 43001L;

    /** 来源提交。 */
    public static final long SOURCE_SUBMISSION_ID = 44001L;

    /** Payment 渠道交易引用。 */
    public static final long CHANNEL_TRANSACTION_REF_ID = 45001L;

    /** 渠道外部交易号。 */
    public static final String CHANNEL_TRANSACTION_REF = "NI-TXN-0001";

    /** 渠道交易引用类型（Payment 动作类型 PAY）。 */
    public static final String CHANNEL_REFERENCE_TYPE = "PAY";

    /** 订单冻结上下文。 */
    public static final long ORDER_CONTEXT_ID = 46001L;

    /** 订单冻结的 Finance 税务快照（acquiring 恒有值）。 */
    public static final long TAX_SNAPSHOT_ID = 47001L;

    /** 订单版本（信封聚合版本）。 */
    public static final long ORDER_VERSION = 3L;

    /** Operating Entity。 */
    public static final String ENTITY_NO = BillingFactRequestBuilder.ENTITY_NO;

    /** 另一个 Operating Entity。 */
    public static final String OTHER_ENTITY_NO = BillingFactRequestBuilder.OTHER_ENTITY_NO;

    /** 事实币种。 */
    public static final String USD = TestCurrencies.USD;

    /** 另一币种。 */
    public static final String EUR = TestCurrencies.EUR;

    /** 支付方式。 */
    public static final String PAYMENT_METHOD = CommercialCodes.PaymentMethod.CARD;

    /** 卡组织。 */
    public static final String CARD_BRAND = CommercialCodes.CardBrand.VISA;

    /** 卡资金类型（commercial 未发布代码常量）。 */
    public static final String FUNDING_TYPE = "CREDIT";

    /** 卡等级（commercial 未发布代码常量）。 */
    public static final String CARD_LEVEL = "CLASSIC";

    /** Payment Channel。 */
    public static final long CHANNEL_ID = 7001L;

    /** 渠道技术账户。 */
    public static final long CHANNEL_ACCOUNT_ID = 7101L;

    /** 渠道商户号。 */
    public static final long MID_ID = 7201L;

    /** 消费者国家。 */
    public static final String CONSUMER_COUNTRY = "AE";

    /** 行业分类代码。 */
    public static final String INDUSTRY_CODE = "DIGITAL_GOODS";

    /** 四位 MCC。 */
    public static final String MCC_CODE = "5815";

    /** 支付成功时间：2026-10-21（周三）08:00Z。 */
    public static final Instant PAYMENT_SUCCEEDED_TIME = Instant.parse("2026-10-21T08:00:00Z");

    /** 冻结商业时点。 */
    public static final Instant COMMERCIAL_CONTEXT_TIME = Instant.parse("2026-10-21T07:00:00Z");

    /** Client / OE 合同。 */
    public static final long CLIENT_AGREEMENT_ID = 51001L;

    /** Client 定价版本。 */
    public static final long CLIENT_PRICING_VERSION_ID = 52001L;

    /** Local Partner。 */
    public static final long LOCAL_PARTNER_ID = 3001L;

    /** Partner 合同。 */
    public static final long PARTNER_AGREEMENT_ID = 53001L;

    /** Partner 合同与 OE 关联。 */
    public static final long PARTNER_AGREEMENT_OE_ID = 53101L;

    /** Partner 分润版本。 */
    public static final long PARTNER_SHARE_VERSION_ID = 54001L;

    /** 平台服务费版本。 */
    public static final long PLATFORM_CHARGE_VERSION_ID = 55001L;

    /** Partner 合同的平台签约 Party。 */
    public static final long PLATFORM_SERVICE_PARTY_ID = 880000000701L;

    /** 平台 Finance Entity。 */
    public static final long FINANCE_ENTITY_ID = 5601L;

    /** Client 价格。 */
    public static final long CLIENT_PRICE_ID = 57001L;

    /** Partner 分润价格。 */
    public static final long PARTNER_SHARE_PRICE_ID = 57101L;

    /** 平台服务费价格。 */
    public static final long SERVICE_CHARGE_PRICE_ID = 57201L;

    /** 渠道成本命中版本。 */
    public static final long COST_VER_ID = 58001L;

    /** 渠道成本组件。 */
    public static final long COST_COMPONENT_ID = 58201L;

    /** 清算账户。 */
    public static final long CLEARING_ACCOUNT_ID = Long.parseLong(BillingFactRequestBuilder.CLEARING_ACCOUNT_ID);

    /** 初始化 DML 的默认工作日日历。 */
    public static final String DEFAULT_CALENDAR = "DEFAULT";

    /** 一日最后一秒。 */
    public static final int END_OF_DAY_CUTOFF = 86_399;

    /** 纯常量。 */
    private PaymentFactConstants() {
    }
}
