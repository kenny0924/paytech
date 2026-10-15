package com.paytech.finance.accounting.core.dal.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.finance.accounting.core.dal.dataobject.FiscalCalendar;
import com.paytech.finance.accounting.core.enums.LedgerEntityType;

/** t_fiscal_calendar：财务日历持久化。 */
@Mapper
public interface FiscalCalendarMapper {

    /** 插入一行。 */
    int insert(FiscalCalendar record);

    /** 按主键读取。 */
    FiscalCalendar selectById(@Param("id") long id);

    /** 以排他锁读取日历，串行化同一日历下的期间创建。 */
    FiscalCalendar selectByIdForUpdate(@Param("id") long id);

    /** 按主体读取唯一财务日历。 */
    FiscalCalendar selectByEntity(@Param("entityType") LedgerEntityType entityType, @Param("entityId") String entityId);
}
