package com.paytech.reconciliation.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 银行账户待复核的变更（t_cash_account.pending_action）
 * <li>为空表示没有待复核的关闭申请；新建待复核由状态 PENDING_VERIFICATION 表示。</li>
 */
public enum CashAccountPendingAction implements CodeEnum {
    /** 申请关闭 */
    CLOSE;

    /** 数据库、接口与事件使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
