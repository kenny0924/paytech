package com.paytech.ledger.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.ledger.core.dal.dataobject.FiscalCalendar;
import com.paytech.ledger.core.enums.LedgerEntityType;
import com.paytech.ledger.domain.FiscalCalendarQuery;

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

    /** 按过滤条件分页读取财务日历，按主键升序。 */
    List<FiscalCalendar> selectPage(@Param("query") FiscalCalendarQuery query, @Param("size") int size,
        @Param("offset") int offset);

    /** 统计符合过滤条件的财务日历数量。 */
    long countPage(@Param("query") FiscalCalendarQuery query);
}
