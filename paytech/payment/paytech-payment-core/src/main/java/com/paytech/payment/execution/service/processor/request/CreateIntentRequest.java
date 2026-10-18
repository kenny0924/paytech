package com.paytech.payment.execution.service.processor.request;

import java.time.Instant;

import com.paytech.foundation.money.Money;

import lombok.Getter;
import lombok.Setter;

/** 创建支付意图请求 */
@Getter
@Setter
public class CreateIntentRequest {
    /** Client ID */
    private Long clientId;

    /** 来源类型 */
    private String sourceType;

    /** 来源订单 ID */
    private Long sourceId;

    /** 经营主体编号 */
    private String entityNo;

    /** 客户定价版本 ID */
    private Long clientPricingVersionId;

    /** 合作伙伴分成版本 ID，可空 */
    private Long partnerShareVersionId;

    /** 平台服务费版本 ID，可空 */
    private Long platformChargeVersionId;

    /** 行业代码 */
    private String industryCode;

    /** MCC 代码 */
    private String mccCode;

    /** 消费者国家代码 */
    private String consumerCountryCode;

    /** 支付金额及币种 */
    private Money amount;

    /** 支付意图到期时间 */
    private Instant expiresAt;
}
