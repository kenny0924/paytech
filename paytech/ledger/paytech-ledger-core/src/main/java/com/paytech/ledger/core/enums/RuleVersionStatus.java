package com.paytech.ledger.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 自动记账规则版本的审批与生效状态。 */
public enum RuleVersionStatus implements CodeEnum {
    /** 草稿，可随版本一次写入规则行 */
    DRAFT,

    /** 已提交待复核，规则行冻结 */
    PENDING_APPROVAL,

    /** 已批准但尚未到生效时间 */
    APPROVED,

    /** 当前生效版本 */
    ACTIVE,

    /** 复核驳回 */
    REJECTED,

    /** 被新版本替代 */
    RETIRED;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
