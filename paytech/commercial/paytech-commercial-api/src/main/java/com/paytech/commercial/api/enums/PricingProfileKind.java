package com.paytech.commercial.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 定价画像描述的定价对象种类（t_pricing_profile.profile_kind）；ACTIVE 后冻结。 */
public enum PricingProfileKind implements CodeEnum {
    /** 支付方式；payment_method_code 必填 */
    PAYMENT_METHOD("PAYMENT_METHOD"),

    /** 出款通道 */
    PAYOUT_RAIL("PAYOUT_RAIL"),

    /** 币种对 */
    FX_PAIR("FX_PAIR"),

    /** 争议 */
    DISPUTE("DISPUTE"),

    /** 周期性服务 */
    PERIODIC("PERIODIC");

    private final String code;

    PricingProfileKind(String code) {
        this.code = code;
    }

    /** 数据库与接口使用的稳定编码。 */
    @Override
    public String getCode() {
        return code;
    }
}
