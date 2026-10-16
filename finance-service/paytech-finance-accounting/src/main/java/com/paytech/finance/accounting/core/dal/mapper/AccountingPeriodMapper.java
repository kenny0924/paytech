package com.paytech.finance.accounting.core.dal.mapper;

import java.time.Instant;
import java.time.LocalDate;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.finance.accounting.core.dal.dataobject.AccountingPeriod;
import com.paytech.finance.accounting.core.enums.PeriodStatus;

/** t_accounting_period：会计期间持久化。 */
@Mapper
public interface AccountingPeriodMapper {

    /** 插入一行。 */
    int insert(AccountingPeriod record);

    /** 按主键读取。 */
    AccountingPeriod selectById(@Param("id") long id);

    /** 以共享锁读取期间；过账持有该锁，关账流程取排他锁即可阻断过账。 */
    AccountingPeriod selectByIdForShare(@Param("id") long id);

    /** 以当前读统计同一日历内与给定日期区间重叠的期间数量。 */
    int countOverlapping(@Param("calendarId") long calendarId, @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate);

    /**
     * 按期望状态和版本迁移期间状态。
     *
     * @return 受影响行数
     */
    int updateStatus(@Param("period") AccountingPeriod period, @Param("status") PeriodStatus status,
        @Param("now") Instant now);

    /** 以排他锁读取期间；关账推进状态时使用，与过账的共享锁互斥。 */
    AccountingPeriod selectByIdForUpdate(@Param("id") long id);

    /** 读取同一日历中结束日期早于给定日期且未 CLOSED 的最早期间。 */
    AccountingPeriod selectFirstUnclosedBefore(@Param("calendarId") long calendarId, @Param("startDate") LocalDate startDate);
}
