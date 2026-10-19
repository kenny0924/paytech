package com.paytech.ledger.domainservice.repository.impl;

import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.FailException;
import com.paytech.ledger.api.enums.LedgerSetupErrorCode;
import com.paytech.ledger.core.dal.dataobject.FiscalCalendar;
import com.paytech.ledger.core.dal.mapper.FiscalCalendarMapper;
import com.paytech.ledger.core.enums.LedgerEntityType;
import com.paytech.ledger.domain.FiscalCalendarQuery;
import com.paytech.ledger.domainservice.repository.FiscalCalendarRepository;

import lombok.AllArgsConstructor;

/** FiscalCalendarRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class FiscalCalendarRepositoryImpl implements FiscalCalendarRepository {
    private final FiscalCalendarMapper mapper;

    /** 插入财务日历；同主体已有日历时拒绝。 */
    @Override
    public void insert(FiscalCalendar calendar) {
        try {
            mapper.insert(calendar);
        } catch (DuplicateKeyException e) {
            throw new FailException(LedgerSetupErrorCode.FISCAL_CALENDAR_ALREADY_EXISTS, null, e);
        }
    }

    /** 按主键读取日历，不存在返回空。 */
    @Override
    public FiscalCalendar findById(long id) {
        return mapper.selectById(id);
    }

    /** 以排他锁读取日历；必须在事务内调用。 */
    @Override
    public FiscalCalendar lockById(long id) {
        return mapper.selectByIdForUpdate(id);
    }

    /** 按主体读取唯一财务日历，不存在返回空。 */
    @Override
    public FiscalCalendar findByEntity(LedgerEntityType entityType, String entityId) {
        return mapper.selectByEntity(entityType, entityId);
    }

    /** 按过滤条件分页读取财务日历，按主键升序。 */
    @Override
    public List<FiscalCalendar> findPage(FiscalCalendarQuery query) {
        return mapper.selectPage(query, query.getPaging().size(), query.getPaging().offset());
    }

    /** 统计符合过滤条件的财务日历数量。 */
    @Override
    public long countPage(FiscalCalendarQuery query) {
        return mapper.countPage(query);
    }
}
