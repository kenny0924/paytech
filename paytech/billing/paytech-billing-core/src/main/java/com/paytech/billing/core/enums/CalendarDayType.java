package com.paytech.billing.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 工作日日历例外日类型（t_business_calendar_day.day_type）。 */
public enum CalendarDayType implements CodeEnum {
    /** 周末调休上班 */
    WORKING,

    /** 节假日 */
    NON_WORKING;

    /** 数据库使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
