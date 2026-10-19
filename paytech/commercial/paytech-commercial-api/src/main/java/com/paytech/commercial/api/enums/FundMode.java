package com.paytech.commercial.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 合同资金模式（t_agreement.fund_mode）；CLIENT 合同必填，本期应用只接受 SETTLE_THROUGH_PLATFORM。 */
public enum FundMode implements CodeEnum {
    /** 交易资金经平台结算后付给 Client；MOR 只允许此值 */
    SETTLE_THROUGH_PLATFORM("SETTLE_THROUGH_PLATFORM"),

    /** 平台只收取费用、不经手交易资金；只允许 OE 来源 CLIENT */
    FEES_ONLY("FEES_ONLY");

    private final String code;

    FundMode(String code) {
        this.code = code;
    }

    /** 数据库与接口使用的稳定编码。 */
    @Override
    public String getCode() {
        return code;
    }
}
