package com.paytech.finance.accounting.domainservice.repository.impl;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.finance.accounting.core.dal.dataobject.FiscalCalendar;
import com.paytech.finance.accounting.core.dal.mapper.FiscalCalendarMapper;
import com.paytech.finance.accounting.core.enums.LedgerEntityType;
import com.paytech.finance.accounting.domainservice.repository.FiscalCalendarRepository;
import com.paytech.foundation.common.error.BizCheckFailException;

import lombok.AllArgsConstructor;

/** FiscalCalendarRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class FiscalCalendarRepositoryImpl implements FiscalCalendarRepository {
    private final FiscalCalendarMapper mapper;

    /** 插入财务日历；同主体已有日历时拒绝。 */
    @Override
    public void insert(FiscalCalendar calendar) {
        insertUnique(() -> mapper.insert(calendar), "财务日历已存在: " + calendar.getEntityType().getCode() + "/" + calendar.getEntityId());
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

    /** 执行插入，业务唯一键冲突时转换为带上下文的业务拒绝。 */
    private void insertUnique(Runnable insert, String message) {
        try {
            insert.run();
        } catch (DuplicateKeyException e) {
            throw new BizCheckFailException(message, e);
        }
    }
}
