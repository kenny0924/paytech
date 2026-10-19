package com.paytech.billing.core.dal.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.billing.core.dal.dataobject.BusinessCalendar;

/** t_business_calendar：工作日日历只读访问。 */
@Mapper
public interface BusinessCalendarMapper {

    /** 按日历代码读取。 */
    BusinessCalendar selectByCode(@Param("calendarCode") String calendarCode);
}
