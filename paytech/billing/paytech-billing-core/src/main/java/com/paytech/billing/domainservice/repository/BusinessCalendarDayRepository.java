package com.paytech.billing.domainservice.repository;

import java.time.LocalDate;
import java.util.List;

import com.paytech.billing.core.dal.dataobject.BusinessCalendarDay;

/** t_business_calendar_day：工作日日历例外日只读访问。 */
public interface BusinessCalendarDayRepository {

    /** 日历在闭区间 [fromDate, toDate] 内的例外日，按日期升序。 */
    List<BusinessCalendarDay> findBetween(String calendarCode, LocalDate fromDate, LocalDate toDate);
}
