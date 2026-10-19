package com.paytech.billing.core.dal.dataobject;

import java.time.Instant;
import java.time.LocalDate;

import com.paytech.billing.core.enums.CalendarDayType;

import lombok.Getter;
import lombok.Setter;

/** t_business_calendar_day：工作日日历的例外日只读副本。 */
@Getter
@Setter
public class BusinessCalendarDay {
    /** Snowflake 主键 */
    private Long id;

    /** 所属日历 */
    private String calendarCode;

    /** 例外日期（日历时区） */
    private LocalDate calendarDate;

    /** 例外类型 */
    private CalendarDayType dayType;

    /**
     * 说明
     * <li>可为空。</li>
     */
    private String note;

    /** 记录创建的 UTC 微秒时间 */
    private Instant createTime;
}
