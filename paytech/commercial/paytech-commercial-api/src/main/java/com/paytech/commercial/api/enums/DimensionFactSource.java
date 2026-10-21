package com.paytech.commercial.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 维度取值的事实来源（代码登记的维度清单，technique.md §2）。 */
public enum DimensionFactSource implements CodeEnum {
    /** 订单与 Checkout 输入 */
    ORDER("ORDER"),

    /** 卡 BIN 识别 */
    CARD_BIN("CARD_BIN"),

    /** 3DS 认证结果 */
    THREE_DS("THREE_DS"),

    /** 交易本身 */
    TRANSACTION("TRANSACTION"),

    /** 出款指令 */
    PAYOUT("PAYOUT"),

    /** 换汇成交 */
    FX("FX");

    private final String code;

    DimensionFactSource(String code) {
        this.code = code;
    }

    /** 接口使用的稳定编码。 */
    @Override
    public String getCode() {
        return code;
    }
}
