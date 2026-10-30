package com.paytech.payout.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 收款账户事件动作（t_beneficiary_account_event.action）
 */
public enum BeneficiaryEventAction implements CodeEnum {
    /** 提交 */
    SUBMIT,

    /** Maker 意见 */
    MAKER_REVIEW,

    /** Checker 决定 */
    CHECKER_DECIDE,

    /** 设为默认 */
    SET_DEFAULT,

    /** 取消默认 */
    UNSET_DEFAULT,

    /** 停用 */
    SUSPEND,

    /** 恢复并重新核验 */
    RESUME,

    /** 关闭 */
    CLOSE,

    /** 查看原值 */
    REVEAL;

    /** 数据库、接口与事件使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
