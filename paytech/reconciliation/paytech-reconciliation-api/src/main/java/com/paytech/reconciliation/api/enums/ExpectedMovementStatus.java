package com.paytech.reconciliation.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 预期银行流水确认进度（t_expected_bank_movement.status）
 * <li>差额经 CLASSIFY 后视为 CONFIRMED。</li>
 */
public enum ExpectedMovementStatus implements CodeEnum {
    /** 尚未确认 */
    OPEN,

    /** 已部分确认 */
    PARTIALLY_CONFIRMED,

    /** 确认金额加归类金额等于预期金额 */
    CONFIRMED,

    /** 出款 / 缴税执行失败或取消（切片 3） */
    CANCELLED,

    /** 存在未结异常 */
    EXCEPTION;

    /** 数据库、接口与事件使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
