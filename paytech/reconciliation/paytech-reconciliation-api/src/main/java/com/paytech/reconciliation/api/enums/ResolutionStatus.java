package com.paytech.reconciliation.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 异常处置状态（t_recon_resolution.status）
 * <li>批准即在同一本地事务应用为 APPLIED，没有单独的 APPROVED 状态。</li>
 */
public enum ResolutionStatus implements CodeEnum {
    /** 已提交，待复核 */
    PENDING_CHECK,

    /** 复核拒绝，异常回到 OPEN */
    REJECTED,

    /** 复核批准并已应用，异常 RESOLVED */
    APPLIED;

    /** 数据库、接口与事件使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
