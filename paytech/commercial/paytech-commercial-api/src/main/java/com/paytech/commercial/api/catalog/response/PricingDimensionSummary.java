package com.paytech.commercial.api.catalog.response;

import lombok.Getter;
import lombok.Setter;

/** 代码登记的一个维度及其元数据。 */
@Getter
@Setter
public class PricingDimensionSummary {
    /** 维度代码 */
    private String dimensionCode;

    /** 运营展示名 */
    private String displayName;

    /** 事实来源：ORDER、CARD_BIN、THREE_DS、TRANSACTION、PAYOUT、FX */
    private String factSource;

    /** 判定时点：CHECKOUT、CARD_ENTRY、AUTHORIZATION、AUTHENTICATION、PAYOUT_CREATION、FX_DEAL */
    private String evaluationPoint;

    /** 是否可用于开通接受条件 */
    private Boolean acceptanceAllowed;

    /** 是否可用于定价画像条件 */
    private Boolean pricingAllowed;
}
