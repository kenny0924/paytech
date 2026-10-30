package com.paytech.payout.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 收款账户状态（t_beneficiary_account.status）
 * <li>提交即 PENDING_REVIEW；REJECTED 与 CLOSED 为终态；VERIFIED 在 coolingUntil 之前可见不可用。</li>
 */
public enum BeneficiaryAccountStatus implements CodeEnum {
    /** 待核验（含停用后恢复的重新核验） */
    PENDING_REVIEW,

    /** 核验通过 */
    VERIFIED,

    /** 核验拒绝（终态） */
    REJECTED,

    /** 运营停用 */
    SUSPENDED,

    /** 已关闭（终态） */
    CLOSED;

    /** 数据库、接口与事件使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
