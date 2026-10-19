package com.paytech.account.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 余额桶移动命令状态（t_balance_movement.movement_status）；移动在一个本地事务内完成，只有已应用一种状态。 */
public enum MovementStatus implements CodeEnum {
    /** 已应用：两条流水与批次已写入 */
    APPLIED;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
