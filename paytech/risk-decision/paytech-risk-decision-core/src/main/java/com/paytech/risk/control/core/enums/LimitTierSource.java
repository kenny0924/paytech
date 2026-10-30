package com.paytech.risk.control.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 档位变更来源，也是当前档位行的来源。 */
public enum LimitTierSource implements CodeEnum {
    /** 承保批准（首次或修订）时 Client 尚无档位，自动建档。 */
    UNDERWRITING_INIT("UNDERWRITING_INIT"),

    /** 存量迁移。 */
    MIGRATION("MIGRATION"),

    /** 运营申请、四眼审批。 */
    REQUEST("REQUEST");

    private final String code;

    LimitTierSource(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
