package com.paytech.reconciliation.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 复核决定（t_recon_resolution.checker_decision；银行账户复核请求）
 */
public enum CheckerDecision implements CodeEnum {
    /** 批准 */
    APPROVED,

    /** 拒绝 */
    REJECTED;

    /** 数据库、接口与事件使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
