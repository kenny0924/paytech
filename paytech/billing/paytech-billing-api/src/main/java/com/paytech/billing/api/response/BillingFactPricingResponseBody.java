package com.paytech.billing.api.response;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** PAYMENT 事实的定价与结算输入（t_billing_fact_pricing）；ID 与整数为十进制字符串。 */
@Getter
@Setter
public class BillingFactPricingResponseBody {
    /** 收单订单上下文冻结的 commercial Client / OE 合同 ID */
    private String clientAgreementId;

    /**
     * 冻结的合作伙伴协议与 OE 关联 ID
     * <li>无合作方为空。</li>
     */
    private String partnerAgreementOeId;

    /** 收单冻结并用于命中合同版本的时间（UTC） */
    private Instant commercialContextTime;

    /** 消费者国家 ISO 3166-1 alpha-2 */
    private String consumerCountryCode;

    /** Payment 支付方式目录代码 */
    private String paymentMethod;

    /**
     * 行业分类代码
     * <li>可为空。</li>
     */
    private String industryCode;

    /**
     * 保留前导零的四位 MCC
     * <li>可为空。</li>
     */
    private String mccCode;

    /**
     * 卡组织代码
     * <li>非卡支付为空。</li>
     */
    private String cardNetwork;

    /**
     * 资金类型（借记 / 贷记 / 预付）代码
     * <li>非卡支付为空。</li>
     */
    private String fundingType;

    /**
     * 卡等级代码
     * <li>非卡支付为空。</li>
     */
    private String cardLevel;

    /**
     * 3DS 认证结果类别代码
     * <li>未认证为空。</li>
     */
    private String authenticationClass;

    /** commercial 命中的 Price ID，费用与到期输入都来自这一行 */
    private String commercialPriceId;

    /** 命中价格对应开通项的非负工作日数 N */
    private String settlementBusinessDays;

    /** 命中价格引用的工作日日历代码 */
    private String businessCalendarCode;

    /** 结算时区（IANA） */
    private String settlementTimezone;

    /** 当日截止秒，超过截止滚至下一工作日 */
    private String cutoffSecondOfDay;
}
