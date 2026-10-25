package com.paytech.commercial.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 商业关系快照的资金流向（relationshipSnapshot.fundFlow）；本轮恒为 THROUGH_CUSTODIAN。 */
public enum FundFlow implements CodeEnum {
    /** 资金先进入资金保管方（收单 OE）再结算 */
    THROUGH_CUSTODIAN("THROUGH_CUSTODIAN"),

    /** 资金直接到 Client；本轮不产出 */
    DIRECT_TO_CLIENT("DIRECT_TO_CLIENT");

    private final String code;

    FundFlow(String code) {
        this.code = code;
    }

    /** 接口使用的稳定编码。 */
    @Override
    public String getCode() {
        return code;
    }
}
