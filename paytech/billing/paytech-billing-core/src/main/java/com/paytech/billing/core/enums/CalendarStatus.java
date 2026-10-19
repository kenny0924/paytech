package com.paytech.billing.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 工作日日历状态（t_business_calendar.status）。 */
public enum CalendarStatus implements CodeEnum {
    /** 可被价格引用 */
    ACTIVE,

    /** 已停用 */
    INACTIVE;

    /** 数据库使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
