package com.paytech.billing.domainservice.repository.impl;

import org.springframework.stereotype.Repository;

import com.paytech.billing.core.dal.dataobject.BusinessCalendar;
import com.paytech.billing.core.dal.mapper.BusinessCalendarMapper;
import com.paytech.billing.domainservice.repository.BusinessCalendarRepository;

import lombok.AllArgsConstructor;

/** BusinessCalendarRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class BusinessCalendarRepositoryImpl implements BusinessCalendarRepository {
    private final BusinessCalendarMapper mapper;

    /** 按日历代码读取，不存在返回空。 */
    @Override
    public BusinessCalendar findByCode(String calendarCode) {
        return mapper.selectByCode(calendarCode);
    }
}
