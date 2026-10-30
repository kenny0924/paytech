package com.paytech.payout.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 复核人（Checker）的决定（checker_decision）
 */
public enum CheckerDecision implements CodeEnum {
    /** 批准，进入冷却 */
    APPROVED,

    /** 拒绝，账户终结 */
    REJECTED;

    /** 数据库、接口与事件使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
