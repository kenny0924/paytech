package com.paytech.billing.domainservice.repository;

import com.paytech.billing.core.dal.dataobject.BusinessCalendar;

/** t_business_calendar：工作日日历只读访问。 */
public interface BusinessCalendarRepository {

    /** 按日历代码读取，不存在返回空。 */
    BusinessCalendar findByCode(String calendarCode);
}
