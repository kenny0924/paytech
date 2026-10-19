package com.paytech.account.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 事实批次中该经济事实在 account 的应用结果
 * <li>SKIPPED：替代事实先到，被替代事实的自身事件后到时只生成 0 流水批次；ledger 不生成任何行，只推进进度。</li>
 */
public enum FactApplyStatus implements CodeEnum {
    /** 已应用（含 0 流水的无 Owner 影响事实，以及替代事实的冲回与新行） */
    APPLIED,

    /** 已被后续版本替代，本事件只占序号 */
    SKIPPED;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
