package com.paytech.finance.accounting.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 关账流程内单个账簿证据的状态。 */
public enum CloseBookStatus implements CodeEnum {
    /** 已锁定水位 */
    LOCKED,

    /** 已冻结最终试算 */
    FINALIZED;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
