package com.paytech.risk.control.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 档位变更申请的审批级别，申请时按有效承保的 CRR 冻结。 */
public enum LimitTierApprovalLevel implements CodeEnum {
    /** CRR=LOW：持有档位审批或高级审批权限者均可处理。 */
    STANDARD("STANDARD"),

    /** CRR=MEDIUM / HIGH、无承保或未评：只能经高级审批接口处理。 */
    ELEVATED("ELEVATED");

    private final String code;

    LimitTierApprovalLevel(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }

    /**
     * 按 CRR 确定审批级别
     *
     * @param crr 有效承保的 CRR；无承保或未评为空
     * @return LOW 为 STANDARD，其余为 ELEVATED
     */
    public static LimitTierApprovalLevel of(UnderwritingRiskLevel crr) {
        return crr == UnderwritingRiskLevel.LOW ? STANDARD : ELEVATED;
    }
}
