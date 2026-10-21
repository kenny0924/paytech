package com.paytech.reconciliation.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 平台银行账户状态（t_cash_account.status）
 */
public enum CashAccountStatus implements CodeEnum {
    /** 新建待复核 */
    PENDING_VERIFICATION,

    /** 已复核生效 */
    ACTIVE,

    /** 暂停使用（本期不产生） */
    SUSPENDED,

    /** 关闭申请复核通过 */
    CLOSED;

    /** 数据库、接口与事件使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
