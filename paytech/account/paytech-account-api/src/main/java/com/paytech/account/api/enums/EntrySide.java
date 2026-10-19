package com.paytech.account.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 借贷方向：明细流水的记账方向，也是 Owner 账户类型的正常余额方向
 * <li>AccountPosted 载荷 {@code entries[].entrySide} 与账户查询 {@code normalSide} 使用本 Code。</li>
 */
public enum EntrySide implements CodeEnum {
    /** 借方 */
    DEBIT,

    /** 贷方 */
    CREDIT;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
