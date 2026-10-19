package com.paytech.billing.domainservice.repository.impl;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.billing.core.dal.dataobject.BusinessCalendarDay;
import com.paytech.billing.core.dal.mapper.BusinessCalendarDayMapper;
import com.paytech.billing.domainservice.repository.BusinessCalendarDayRepository;

import lombok.AllArgsConstructor;

/** BusinessCalendarDayRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class BusinessCalendarDayRepositoryImpl implements BusinessCalendarDayRepository {
    private final BusinessCalendarDayMapper mapper;

    /** 日历在闭区间内的例外日。 */
    @Override
    public List<BusinessCalendarDay> findBetween(String calendarCode, LocalDate fromDate, LocalDate toDate) {
        return mapper.selectByCalendarBetween(calendarCode, fromDate, toDate);
    }
}
