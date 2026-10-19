package com.paytech.billing.core.dal.dataobject;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/**
 * t_billing_fact_pricing：PAYMENT 事实的定价输入与结算输入，与事实头 1:1；不可变
 * <li>本期没有生产写入方（自动支付事实在后续切片），只读路径返回空。</li>
 */
@Getter
@Setter
public class BillingFactPricing {
    /** 对应的 PAYMENT 计费事实 */
    private Long factId;

    /** 收单订单上下文冻结的 commercial Client / OE 合同 ID */
    private Long clientAgreementId;

    /**
     * 冻结的合作伙伴协议与 OE 关联 ID
     * <li>无合作方为空。</li>
     */
    private Long partnerAgreementOeId;

    /** 收单冻结并用于命中合同版本的 UTC 微秒时间 */
    private Instant commercialContextTime;

    /** 消费者国家 ISO 3166-1 alpha-2 */
    private String consumerCountryCode;

    /** Payment 支付方式目录代码 */
    private String paymentMethod;

    /**
     * 行业分类代码
     * <li>可为空；非固定枚举。</li>
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
     * 资金类型代码
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

    /** commercial 命中的 Price */
    private Long commercialPriceId;

    /** 命中价格对应开通项的非负工作日数 N */
    private Integer settlementBusinessDays;

    /** 命中价格引用的工作日日历代码 */
    private String businessCalendarCode;

    /** 结算时区（IANA） */
    private String settlementTimezone;

    /** 当日截止秒 */
    private Integer cutoffSecondOfDay;

    /** 记录创建的 UTC 微秒时间 */
    private Instant createTime;
}
