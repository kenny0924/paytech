package com.paytech.finance.accounting.domainservice.repository.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.finance.accounting.core.dal.dataobject.AccountingPeriod;
import com.paytech.finance.accounting.core.dal.mapper.AccountingPeriodMapper;
import com.paytech.finance.accounting.core.enums.PeriodStatus;
import com.paytech.finance.accounting.domainservice.repository.AccountingPeriodRepository;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;

import lombok.AllArgsConstructor;

/** AccountingPeriodRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class AccountingPeriodRepositoryImpl implements AccountingPeriodRepository {
    private final Clock clock;
    private final AccountingPeriodMapper mapper;

    /** 插入会计期间；同一日历的年度与期号已存在时拒绝。 */
    @Override
    public void insert(AccountingPeriod period) {
        insertUnique(() -> mapper.insert(period), "会计期间已存在: 日历 " + period.getCalendarId() + ", " + period.getFiscalYear() + "-" + period.getPeriodNo());
    }

    /** 按主键读取期间，不存在返回空。 */
    @Override
    public AccountingPeriod findById(long id) {
        return mapper.selectById(id);
    }

    /** 以共享锁读取期间；必须在事务内调用。 */
    @Override
    public AccountingPeriod lockSharedById(long id) {
        return mapper.selectByIdForShare(id);
    }

    /** 统计同一日历内与给定区间重叠的期间数量。 */
    @Override
    public int countOverlapping(long calendarId, LocalDate startDate, LocalDate endDate) {
        return mapper.countOverlapping(calendarId, startDate, endDate);
    }

    /** 按原状态与版本迁移期间状态，成功后同步原对象。 */
    @Override
    public void updateStatus(AccountingPeriod period, PeriodStatus status) {
        Instant now = now();
        if (mapper.updateStatus(period, status, now) != 1) {
            throw new ConcurrentRequestException("会计期间状态已变化: " + period.getId());
        }
        period.setStatus(status);
        period.setVersion(period.getVersion() + 1);
        period.setUpdateTime(now);
    }

    /** 以排他锁读取期间；必须在事务内调用。 */
    @Override
    public AccountingPeriod lockById(long id) {
        return mapper.selectByIdForUpdate(id);
    }

    /** 读取同一日历中早于给定日期且未关闭的最早期间，不存在返回空。 */
    @Override
    public AccountingPeriod findFirstUnclosedBefore(long calendarId, LocalDate startDate) {
        return mapper.selectFirstUnclosedBefore(calendarId, startDate);
    }

    /** 读取同一日历中开始晚于给定日期且已关闭、关账中或重开中的最早期间，不存在返回空。 */
    @Override
    public AccountingPeriod findFirstClosedAfter(long calendarId, LocalDate startDate) {
        return mapper.selectFirstClosedAfter(calendarId, startDate);
    }

    /** 重开完成：REOPENING → OPEN 且重开次数加一，成功后同步原对象。 */
    @Override
    public void completeReopen(AccountingPeriod period) {
        Instant now = now();
        if (mapper.completeReopen(period, now) != 1) {
            throw new ConcurrentRequestException("会计期间状态已变化: " + period.getId());
        }
        period.setStatus(PeriodStatus.OPEN);
        period.setReopenCount(period.getReopenCount() + 1);
        period.setVersion(period.getVersion() + 1);
        period.setUpdateTime(now);
    }

    /** 读取日历中包含给定日期的期间，不存在返回空。 */
    @Override
    public AccountingPeriod findByCalendarAndDate(long calendarId, LocalDate date) {
        return mapper.selectByCalendarAndDate(calendarId, date);
    }

    /** 当前 UTC 时间，截断到数据库支持的微秒精度。 */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /** 执行插入，业务唯一键冲突时转换为带上下文的业务拒绝。 */
    private void insertUnique(Runnable insert, String message) {
        try {
            insert.run();
        } catch (DuplicateKeyException e) {
            throw new BizCheckFailException(message, e);
        }
    }
}
