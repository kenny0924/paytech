package com.paytech.ledger.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 科目启停状态；与永久结构属性 posting_allowed 无关。 */
public enum ChartAccountStatus implements CodeEnum {
    /** 启用：可新建账户、可被新规则版本引用 */
    ACTIVE,

    /** 停用：不能新建账户、不能被新规则版本引用；已有账户照常记账与查询 */
    INACTIVE;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
