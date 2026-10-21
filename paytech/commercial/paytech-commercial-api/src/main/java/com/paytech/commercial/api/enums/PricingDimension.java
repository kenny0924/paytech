package com.paytech.commercial.api.enums;

import static com.paytech.commercial.api.enums.DimensionEvaluationPoint.AUTHENTICATION;
import static com.paytech.commercial.api.enums.DimensionEvaluationPoint.AUTHORIZATION;
import static com.paytech.commercial.api.enums.DimensionEvaluationPoint.CARD_ENTRY;
import static com.paytech.commercial.api.enums.DimensionEvaluationPoint.CHECKOUT;
import static com.paytech.commercial.api.enums.DimensionEvaluationPoint.FX_DEAL;
import static com.paytech.commercial.api.enums.DimensionEvaluationPoint.PAYOUT_CREATION;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 定价画像条件与开通项接受条件的维度（t_pricing_profile_cond.dimension_code、t_agreement_capability_limit.limit_code、
 * t_product_capability_dimension.dimension_code）
 * <li>维度清单由代码登记（technique.md §2）：事实来源、判定时点、是否可用于开通接受条件、是否可用于定价画像。</li>
 */
public enum PricingDimension implements CodeEnum {
    /** 卡组织品牌 */
    CARD_BRAND("CARD_BRAND", "卡组织", DimensionFactSource.CARD_BIN, CARD_ENTRY, true, true),

    /** 卡资金类型 */
    FUNDING_TYPE("FUNDING_TYPE", "借贷记类型", DimensionFactSource.CARD_BIN, CARD_ENTRY, true, true),

    /** 卡等级 */
    CARD_LEVEL("CARD_LEVEL", "卡等级", DimensionFactSource.CARD_BIN, AUTHORIZATION, true, true),

    /** 卡产品 */
    CARD_PRODUCT("CARD_PRODUCT", "卡产品", DimensionFactSource.CARD_BIN, AUTHORIZATION, true, true),

    /** 发卡行区域 */
    ISSUER_REGION("ISSUER_REGION", "发卡区域", DimensionFactSource.CARD_BIN, CARD_ENTRY, true, true),

    /** 发卡行国家 */
    ISSUER_COUNTRY("ISSUER_COUNTRY", "发卡国家", DimensionFactSource.CARD_BIN, CARD_ENTRY, true, true),

    /** 3DS 认证结果 */
    THREE_DS_RESULT("THREE_DS_RESULT", "3DS 结果", DimensionFactSource.THREE_DS, AUTHENTICATION, false, true),

    /** 交易币种 */
    CURRENCY("CURRENCY", "币种", DimensionFactSource.ORDER, CHECKOUT, true, true),

    /** 消费者国家 */
    CONSUMER_COUNTRY("CONSUMER_COUNTRY", "消费者国家", DimensionFactSource.ORDER, CHECKOUT, true, true),

    /** 交易类型 */
    TRANSACTION_TYPE("TRANSACTION_TYPE", "交易类型", DimensionFactSource.TRANSACTION, AUTHORIZATION, false, true),

    /** 出款通道 */
    PAYOUT_RAIL("PAYOUT_RAIL", "出款通道", DimensionFactSource.PAYOUT, PAYOUT_CREATION, true, true),

    /** 出款目的国家 */
    DESTINATION_COUNTRY("DESTINATION_COUNTRY", "出款目的国家", DimensionFactSource.PAYOUT, PAYOUT_CREATION, true, true),

    /** 币种对 */
    CURRENCY_PAIR("CURRENCY_PAIR", "币种对", DimensionFactSource.FX, FX_DEAL, true, true),

    /** 争议阶段 */
    DISPUTE_STAGE("DISPUTE_STAGE", "争议阶段", DimensionFactSource.TRANSACTION, AUTHORIZATION, false, true);

    private final String code;

    private final String displayName;

    private final DimensionFactSource factSource;

    private final DimensionEvaluationPoint evaluationPoint;

    private final boolean acceptanceAllowed;

    private final boolean pricingAllowed;

    PricingDimension(String code, String displayName, DimensionFactSource factSource,
        DimensionEvaluationPoint evaluationPoint, boolean acceptanceAllowed, boolean pricingAllowed) {
        this.code = code;
        this.displayName = displayName;
        this.factSource = factSource;
        this.evaluationPoint = evaluationPoint;
        this.acceptanceAllowed = acceptanceAllowed;
        this.pricingAllowed = pricingAllowed;
    }

    /** 数据库与接口使用的稳定编码。 */
    @Override
    public String getCode() {
        return code;
    }

    /** 运营展示名。 */
    public String getDisplayName() {
        return displayName;
    }

    /** 取值的事实来源。 */
    public DimensionFactSource getFactSource() {
        return factSource;
    }

    /** 取值可被判定的最早时点。 */
    public DimensionEvaluationPoint getEvaluationPoint() {
        return evaluationPoint;
    }

    /** 是否可作为合同开通项的接受条件。 */
    public boolean isAcceptanceAllowed() {
        return acceptanceAllowed;
    }

    /** 是否可作为定价画像条件。 */
    public boolean isPricingAllowed() {
        return pricingAllowed;
    }
}
