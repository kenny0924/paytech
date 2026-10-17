package com.paytech.ledger.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 试算平衡完整性异常分类。 */
public enum TrialExceptionType implements CodeEnum {
    /** 往来双边不一致 */
    COUNTERPARTY_MISMATCH,

    /** 渠道清算阶段差异 */
    CHANNEL_SETTLEMENT_MISMATCH,

    /** 总账投影或借贷合计不一致 */
    GENERAL_LEDGER_MISMATCH,

    /** 未解决的对账差异 */
    UNRESOLVED_RECONCILIATION;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
