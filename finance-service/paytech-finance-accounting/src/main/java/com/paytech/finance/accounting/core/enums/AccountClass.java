package com.paytech.finance.accounting.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 会计恒等式中的五类科目分类。 */
public enum AccountClass implements CodeEnum {
    /** 资产 */
    ASSET,

    /** 负债 */
    LIABILITY,

    /** 权益 */
    EQUITY,

    /** 收入 */
    REVENUE,

    /** 费用 */
    EXPENSE;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
