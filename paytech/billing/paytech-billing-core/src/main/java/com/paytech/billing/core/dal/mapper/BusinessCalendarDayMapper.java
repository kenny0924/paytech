package com.paytech.billing.core.dal.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.billing.core.dal.dataobject.BusinessCalendarDay;

/** t_business_calendar_day：工作日日历例外日只读访问。 */
@Mapper
public interface BusinessCalendarDayMapper {

    /** 日历在闭区间 [fromDate, toDate] 内的例外日，按日期升序。 */
    List<BusinessCalendarDay> selectByCalendarBetween(@Param("calendarCode") String calendarCode,
        @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);
}
