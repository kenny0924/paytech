package com.paytech.ledger.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 审批人对冻结调整内容的决定。 */
public enum ApprovalDecision implements CodeEnum {
    /** 批准 */
    APPROVED,

    /** 驳回 */
    REJECTED;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
