package com.paytech.finance.accounting.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 人工调整审批记录承担的角色。 */
public enum ApprovalRole implements CodeEnum {
    /** 编制人 */
    MAKER,

    /** 复核人 */
    CHECKER;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
