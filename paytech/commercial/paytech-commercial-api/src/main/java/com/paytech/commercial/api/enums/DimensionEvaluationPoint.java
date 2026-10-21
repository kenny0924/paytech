package com.paytech.commercial.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 维度取值可被判定的最早时点（代码登记的维度清单，technique.md §2）。 */
public enum DimensionEvaluationPoint implements CodeEnum {
    /** 创建 Checkout 时 */
    CHECKOUT("CHECKOUT"),

    /** 消费者录入卡号时 */
    CARD_ENTRY("CARD_ENTRY"),

    /** 授权时 */
    AUTHORIZATION("AUTHORIZATION"),

    /** 3DS 认证完成时 */
    AUTHENTICATION("AUTHENTICATION"),

    /** 创建出款时 */
    PAYOUT_CREATION("PAYOUT_CREATION"),

    /** 换汇成交时 */
    FX_DEAL("FX_DEAL");

    private final String code;

    DimensionEvaluationPoint(String code) {
        this.code = code;
    }

    /** 接口使用的稳定编码。 */
    @Override
    public String getCode() {
        return code;
    }
}
