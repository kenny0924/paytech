package com.paytech.billing.domain.view;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** PAYMENT 事实的定价与结算输入（t_billing_fact_pricing 的查询结果，不含主键与技术列）。 */
@Getter
@Setter
public class BillingFactPricingView {
    /** commercial Client / OE 合同 ID。 */
    private Long clientAgreementId;

    /**
     * 合作伙伴协议与 OE 关联 ID
     * <li>无合作方为空。</li>
     */
    private Long partnerAgreementOeId;

    /** 商业上下文时间。 */
    private Instant commercialContextTime;

    /** 消费者国家。 */
    private String consumerCountryCode;

    /** 支付方式代码。 */
    private String paymentMethod;

    /** 行业代码；可为空。 */
    private String industryCode;

    /** MCC；可为空。 */
    private String mccCode;

    /** 卡组织；可为空。 */
    private String cardNetwork;

    /** 资金类型；可为空。 */
    private String fundingType;

    /** 卡等级；可为空。 */
    private String cardLevel;

    /** 认证类别；可为空。 */
    private String authenticationClass;

    /** 命中 Price ID。 */
    private Long commercialPriceId;

    /** 结算工作日数。 */
    private Integer settlementBusinessDays;

    /** 工作日日历代码。 */
    private String businessCalendarCode;

    /** 结算时区。 */
    private String settlementTimezone;

    /** 当日截止秒。 */
    private Integer cutoffSecondOfDay;
}
