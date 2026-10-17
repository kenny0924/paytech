package com.paytech.account.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 账户或科目余额增加时采用的会计方向
 * <li>副本：Owner 为 ledger（{@code com.paytech.ledger.core.enums.NormalSide}），本期只用于只读校验共库 t_account 投影；第 11b 步随余额表收口。</li>
 */
public enum NormalSide implements CodeEnum {
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
