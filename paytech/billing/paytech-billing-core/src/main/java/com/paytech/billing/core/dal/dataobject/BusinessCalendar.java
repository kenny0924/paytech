package com.paytech.billing.core.dal.dataobject;

import java.time.Instant;

import com.paytech.billing.core.enums.CalendarStatus;

import lombok.Getter;
import lombok.Setter;

/**
 * t_business_calendar：工作日日历只读副本
 * <li>默认周一至周五为工作日，例外日见 t_business_calendar_day；本服务只读，初始化 DML 登记 DEFAULT。</li>
 */
@Getter
@Setter
public class BusinessCalendar {
    /** 工作日日历代码，commercial Price 的 business_calendar_code 引用它 */
    private String calendarCode;

    /** 显示名 */
    private String displayName;

    /** 日历所在时区（IANA） */
    private String timezone;

    /** 状态 */
    private CalendarStatus status;

    /** 记录创建的 UTC 微秒时间 */
    private Instant createTime;

    /** 记录最近一次持久化更新的 UTC 微秒时间 */
    private Instant updateTime;
}
