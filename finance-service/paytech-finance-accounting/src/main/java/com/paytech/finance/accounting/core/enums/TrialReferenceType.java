package com.paytech.finance.accounting.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 试算异常引用对象类型。 */
public enum TrialReferenceType implements CodeEnum {
    /** 统一记账账户 */
    ACCOUNT,

    /** 会计科目或科目表 */
    CHART_ACCOUNT,

    /** 银行账户 */
    CASH_ACCOUNT,

    /** 对账异常 */
    RECON_EXCEPTION;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
