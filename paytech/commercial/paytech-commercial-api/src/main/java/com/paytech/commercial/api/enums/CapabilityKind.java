package com.paytech.commercial.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 可开通项所属的能力种类（t_product_capability.capability_kind）；决定是否绑定 Payment Method。 */
public enum CapabilityKind implements CodeEnum {
    /** 支付方式；payment_method_code 必填 */
    PAYMENT_METHOD("PAYMENT_METHOD"),

    /** 功能（如存卡、周期扣款）；payment_method_code 为空 */
    FEATURE("FEATURE"),

    /** 出款通道 */
    PAYOUT_RAIL("PAYOUT_RAIL"),

    /** 换汇 */
    FX("FX"),

    /** 争议处理 */
    DISPUTE("DISPUTE"),

    /** 周期性订阅服务 */
    PERIODIC_SERVICE("PERIODIC_SERVICE");

    private final String code;

    CapabilityKind(String code) {
        this.code = code;
    }

    /** 数据库与接口使用的稳定编码。 */
    @Override
    public String getCode() {
        return code;
    }
}
