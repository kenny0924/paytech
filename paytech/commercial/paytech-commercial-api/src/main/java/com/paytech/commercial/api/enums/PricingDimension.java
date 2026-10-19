package com.paytech.commercial.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 定价画像条件与开通项细分维度（t_pricing_profile_cond.dimension_code、t_product_capability.dimension_code）。 */
public enum PricingDimension implements CodeEnum {
    /** 卡组织品牌 */
    CARD_BRAND("CARD_BRAND"),

    /** 卡资金类型 */
    FUNDING_TYPE("FUNDING_TYPE"),

    /** 卡等级 */
    CARD_LEVEL("CARD_LEVEL"),

    /** 卡产品 */
    CARD_PRODUCT("CARD_PRODUCT"),

    /** 发卡行区域 */
    ISSUER_REGION("ISSUER_REGION"),

    /** 发卡行国家 */
    ISSUER_COUNTRY("ISSUER_COUNTRY"),

    /** 3DS 认证结果 */
    THREE_DS_RESULT("THREE_DS_RESULT"),

    /** 交易币种 */
    CURRENCY("CURRENCY"),

    /** 消费者国家 */
    CONSUMER_COUNTRY("CONSUMER_COUNTRY"),

    /** 交易类型 */
    TRANSACTION_TYPE("TRANSACTION_TYPE"),

    /** 出款通道 */
    PAYOUT_RAIL("PAYOUT_RAIL"),

    /** 出款目的国家 */
    DESTINATION_COUNTRY("DESTINATION_COUNTRY"),

    /** 币种对 */
    CURRENCY_PAIR("CURRENCY_PAIR"),

    /** 争议阶段 */
    DISPUTE_STAGE("DISPUTE_STAGE");

    private final String code;

    PricingDimension(String code) {
        this.code = code;
    }

    /** 数据库与接口使用的稳定编码。 */
    @Override
    public String getCode() {
        return code;
    }
}
