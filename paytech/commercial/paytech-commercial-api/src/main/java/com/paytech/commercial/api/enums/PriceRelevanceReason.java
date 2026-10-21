package com.paytech.commercial.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 价格与合同开通项不相关的原因（保存提示、激活拦截）。 */
public enum PriceRelevanceReason implements CodeEnum {
    /** 画像支付方式在该 OE 没有 ENABLED 开通项 */
    PAYMENT_METHOD_NOT_ENABLED("PAYMENT_METHOD_NOT_ENABLED"),

    /** 价格币种不在开通项币种条件内 */
    CURRENCY_NOT_ENABLED("CURRENCY_NOT_ENABLED"),

    /** 画像条件与开通项接受条件没有交集 */
    CONDITION_OUT_OF_SCOPE("CONDITION_OUT_OF_SCOPE"),

    /** 功能类计费事件没有对应的功能开通项 */
    FEATURE_NOT_ENABLED("FEATURE_NOT_ENABLED"),

    /** 价格所在 OE 没有任何与本方案计费事件相关的开通项 */
    NO_CAPABILITY_ON_ENTITY("NO_CAPABILITY_ON_ENTITY");

    private final String code;

    PriceRelevanceReason(String code) {
        this.code = code;
    }

    /** 接口使用的稳定编码。 */
    @Override
    public String getCode() {
        return code;
    }
}
