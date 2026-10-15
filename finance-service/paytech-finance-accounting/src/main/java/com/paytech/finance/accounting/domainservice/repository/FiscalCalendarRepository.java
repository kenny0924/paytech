package com.paytech.finance.accounting.domainservice.repository;

import com.paytech.finance.accounting.core.dal.dataobject.FiscalCalendar;
import com.paytech.finance.accounting.core.enums.LedgerEntityType;

/** t_FiscalCalendar：财务日历读写。 */
public interface FiscalCalendarRepository {

    /** 插入财务日历；同主体已有日历时拒绝。 */
    void insert(FiscalCalendar calendar);

    /** 按主键读取日历，不存在返回空。 */
    FiscalCalendar findById(long id);

    /** 以排他锁读取日历；必须在事务内调用。 */
    FiscalCalendar lockById(long id);

    /** 按主体读取唯一财务日历，不存在返回空。 */
    FiscalCalendar findByEntity(LedgerEntityType entityType, String entityId);
}
