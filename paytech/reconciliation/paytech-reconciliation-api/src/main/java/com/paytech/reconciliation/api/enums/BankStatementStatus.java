package com.paytech.reconciliation.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 银行对账单导入状态（t_bank_statement.status）
 */
public enum BankStatementStatus implements CodeEnum {
    /** 分段接收流水行中 */
    RECEIVING,

    /** 完成导入 */
    IMPORTED,

    /** 完成导入时校验不通过，整单拒绝 */
    REJECTED;

    /** 数据库、接口与事件使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
