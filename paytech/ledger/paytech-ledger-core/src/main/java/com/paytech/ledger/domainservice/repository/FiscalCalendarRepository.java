package com.paytech.ledger.domainservice.repository;

import java.util.List;

import com.paytech.ledger.core.dal.dataobject.FiscalCalendar;
import com.paytech.ledger.core.enums.LedgerEntityType;
import com.paytech.ledger.domain.FiscalCalendarQuery;

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

    /** 按过滤条件分页读取财务日历，按主键升序。 */
    List<FiscalCalendar> findPage(FiscalCalendarQuery query);

    /** 统计符合过滤条件的财务日历数量。 */
    long countPage(FiscalCalendarQuery query);
}
