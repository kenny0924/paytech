package com.paytech.ledger.domainservice.repository;

import java.time.LocalDate;

import com.paytech.ledger.core.dal.dataobject.AccountingPeriod;
import com.paytech.ledger.core.enums.PeriodStatus;

/** t_AccountingPeriod：会计期间读写。 */
public interface AccountingPeriodRepository {

    /** 插入会计期间；同一日历的年度与期号已存在时拒绝。 */
    void insert(AccountingPeriod period);

    /** 按主键读取期间，不存在返回空。 */
    AccountingPeriod findById(long id);

    /** 以共享锁读取期间；必须在事务内调用。 */
    AccountingPeriod lockSharedById(long id);

    /** 统计同一日历内与给定区间重叠的期间数量。 */
    int countOverlapping(long calendarId, LocalDate startDate, LocalDate endDate);

    /** 按原状态与版本迁移期间状态，成功后同步原对象。 */
    void updateStatus(AccountingPeriod period, PeriodStatus status);

    /** 以排他锁读取期间；必须在事务内调用。 */
    AccountingPeriod lockById(long id);

    /** 读取同一日历中早于给定日期且未关闭的最早期间，不存在返回空。 */
    AccountingPeriod findFirstUnclosedBefore(long calendarId, LocalDate startDate);

    /** 读取同一日历中开始晚于给定日期且已关闭、关账中或重开中的最早期间，不存在返回空。 */
    AccountingPeriod findFirstClosedAfter(long calendarId, LocalDate startDate);

    /** 重开完成：REOPENING → OPEN 且重开次数加一，成功后同步原对象。 */
    void completeReopen(AccountingPeriod period);

    /** 读取日历中包含给定日期的期间，不存在返回空。 */
    AccountingPeriod findByCalendarAndDate(long calendarId, LocalDate date);
}
