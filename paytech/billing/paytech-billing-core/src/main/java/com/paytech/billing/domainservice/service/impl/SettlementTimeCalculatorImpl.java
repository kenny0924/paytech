package com.paytech.billing.domainservice.service.impl;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.paytech.billing.core.dal.dataobject.BusinessCalendar;
import com.paytech.billing.core.dal.dataobject.BusinessCalendarDay;
import com.paytech.billing.core.enums.CalendarDayType;
import com.paytech.billing.core.enums.CalendarStatus;
import com.paytech.billing.domain.PriceSettlement;
import com.paytech.billing.domainservice.repository.BusinessCalendarDayRepository;
import com.paytech.billing.domainservice.repository.BusinessCalendarRepository;
import com.paytech.billing.domainservice.service.SettlementTimeCalculator;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/**
 * T+N 到期转可用时间推算：一次读取起算日之后足够长窗口内的例外日，再逐日判定工作日
 * <li>窗口为起算日起 {@value #WINDOW_BASE_DAYS} + 3N 天；窗口内凑不满 N 个工作日（日历几乎全为非工作日）视为配置缺口。</li>
 */
@Service
@AllArgsConstructor
public class SettlementTimeCalculatorImpl implements SettlementTimeCalculator {
    /** 例外日读取窗口的基础天数。 */
    static final int WINDOW_BASE_DAYS = 31;

    /** 一日最后一秒（秒数上限）。 */
    static final int LAST_SECOND_OF_DAY = 86_399;

    /** 到期时刻：结算时区当日 23:59:59。 */
    static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59);

    private final BusinessCalendarRepository businessCalendarRepository;
    private final BusinessCalendarDayRepository businessCalendarDayRepository;

    /** 校验结算字段与日历后按起算日、工作日推算。 */
    @Override
    public Instant balanceAvailableTime(Instant businessTime, PriceSettlement settlement) {
        requireValid(settlement);
        ZoneId zone = zoneOf(settlement.getSettlementTimezone());
        requireActiveCalendar(settlement.getBusinessCalendarCode());
        LocalDate startDate = startDate(businessTime.atZone(zone), settlement.getCutoffSecondOfDay());
        LocalDate dueDate = dueDate(settlement.getBusinessCalendarCode(), startDate,
            settlement.getSettlementBusinessDays());
        return dueDate.atTime(END_OF_DAY).atZone(zone).toInstant();
    }

    /** 结算四字段齐全、N 非负、截止秒在一日之内。 */
    static void requireValid(PriceSettlement settlement) {
        if (settlement == null || !settlement.complete()) {
            throw new ErrorException("命中价格缺少结算字段，不能推算到期时间");
        }
        boolean valid = settlement.getSettlementBusinessDays() >= 0 && settlement.getCutoffSecondOfDay() >= 0
            && settlement.getCutoffSecondOfDay() <= LAST_SECOND_OF_DAY;
        if (!valid) {
            throw new ErrorException("命中价格结算字段非法: N " + settlement.getSettlementBusinessDays() + ", 截止秒 "
                + settlement.getCutoffSecondOfDay());
        }
    }

    /** IANA 时区。 */
    static ZoneId zoneOf(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException invalid) {
            throw new ErrorException("命中价格结算时区非法: " + timezone, invalid);
        }
    }

    /** 日历必须存在且启用。 */
    void requireActiveCalendar(String calendarCode) {
        BusinessCalendar calendar = businessCalendarRepository.findByCode(calendarCode);
        if (calendar == null || calendar.getStatus() != CalendarStatus.ACTIVE) {
            throw new ErrorException("工作日日历不存在或未启用: " + calendarCode);
        }
    }

    /** 起算日：结算时区日期，日内秒数超过截止秒时为次日。 */
    static LocalDate startDate(ZonedDateTime localTime, int cutoffSecondOfDay) {
        LocalDate date = localTime.toLocalDate();
        return localTime.toLocalTime().toSecondOfDay() > cutoffSecondOfDay ? date.plusDays(1) : date;
    }

    /** N = 0 取起算日或其后首个工作日；N &gt; 0 取起算日之后第 N 个工作日。 */
    LocalDate dueDate(String calendarCode, LocalDate startDate, int businessDays) {
        LocalDate windowEnd = startDate.plusDays(WINDOW_BASE_DAYS + 3L * businessDays);
        Map<LocalDate, CalendarDayType> exceptions = businessCalendarDayRepository
            .findBetween(calendarCode, startDate, windowEnd).stream()
            .collect(Collectors.toMap(BusinessCalendarDay::getCalendarDate, BusinessCalendarDay::getDayType));
        LocalDate date = startDate;
        int counted = 0;
        while (businessDays == 0 ? !workingDay(date, exceptions) : counted < businessDays) {
            date = date.plusDays(1);
            counted += workingDay(date, exceptions) ? 1 : 0;
            if (date.isAfter(windowEnd)) {
                throw new ErrorException("工作日日历在窗口内工作日不足: " + calendarCode + ", 起算日 " + startDate);
            }
        }
        return date;
    }

    /** 例外日记录优先；无记录时周一至周五为工作日。 */
    static boolean workingDay(LocalDate date, Map<LocalDate, CalendarDayType> exceptions) {
        CalendarDayType exception = exceptions.get(date);
        if (exception != null) {
            return exception == CalendarDayType.WORKING;
        }
        return date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY;
    }
}
