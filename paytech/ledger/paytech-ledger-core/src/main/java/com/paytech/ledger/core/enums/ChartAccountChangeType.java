package com.paytech.ledger.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 科目变更记录的变更类型。 */
public enum ChartAccountChangeType implements CodeEnum {
    /** 新增科目 */
    CREATE,

    /** 修改名称、编号或上级 */
    UPDATE,

    /** 停用 */
    DISABLE,

    /** 启用 */
    ENABLE;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
