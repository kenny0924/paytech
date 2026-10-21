package com.paytech.reconciliation.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 银行账户与对账异常的主体类型（entity_type）
 */
public enum ReconEntityType implements CodeEnum {
    /** 经营主体，entityId 为 entityNo */
    OPERATING_ENTITY,

    /** 财务主体，entityId 为 ledger 主键十进制字符串 */
    FINANCE_ENTITY;

    /** 数据库、接口与事件使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
