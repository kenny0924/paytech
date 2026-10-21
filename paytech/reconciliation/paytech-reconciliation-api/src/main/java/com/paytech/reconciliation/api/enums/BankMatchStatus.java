package com.paytech.reconciliation.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 银行匹配状态（t_bank_match.status）
 * <li>只有 CONFIRMED 产生下游事件。</li>
 */
public enum BankMatchStatus implements CodeEnum {
    /** 系统一对一建议，待运营确认或拒绝 */
    PROPOSED,

    /** 已确认 */
    CONFIRMED,

    /** 建议被拒绝 */
    REJECTED;

    /** 数据库、接口与事件使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
