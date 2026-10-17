package com.paytech.ledger.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 账簿和财务日历的主体类型。 */
public enum LedgerEntityType implements CodeEnum {
    /** CCT 经营主体，entityId 为 entityNo */
    OPERATING_ENTITY,

    /** Finance 内部财务主体，entityId 为本地主键 */
    FINANCE_ENTITY;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
