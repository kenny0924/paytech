package com.paytech.finance.accounting.domainservice.repository.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.finance.accounting.core.dal.dataobject.PeriodClose;
import com.paytech.finance.accounting.core.dal.mapper.PeriodCloseMapper;
import com.paytech.finance.accounting.core.enums.PeriodCloseStatus;
import com.paytech.finance.accounting.core.enums.PeriodCloseType;
import com.paytech.finance.accounting.domain.view.PeriodCloseView;
import com.paytech.finance.accounting.domainservice.repository.PeriodCloseRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;

import lombok.AllArgsConstructor;

/** PeriodCloseRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class PeriodCloseRepositoryImpl implements PeriodCloseRepository {
    private final Clock clock;
    private final PeriodCloseMapper mapper;

    /** 插入流程根；同一期间已有活动流程或序号重复时按并发冲突拒绝。 */
    @Override
    public void insert(PeriodClose close) {
        try {
            mapper.insert(close);
        } catch (DuplicateKeyException e) {
            throw new ConcurrentRequestException("会计期间已有进行中的关账或重开流程: " + close.getPeriodId(), e);
        }
    }

    /** 同一期间的下一个关账序号。 */
    @Override
    public int nextCloseNo(long periodId) {
        return mapper.selectNextCloseNo(periodId);
    }

    /** 按期间与序号读取流程根，不存在返回空。 */
    @Override
    public PeriodClose findByPeriodAndNo(long periodId, int closeNo) {
        return mapper.selectByPeriodAndNo(periodId, closeNo);
    }

    /** 按主键读取流程根，不存在返回空。 */
    @Override
    public PeriodClose findById(long id) {
        return mapper.selectById(id);
    }

    /** 读取期间内指定类型序号最大的流程根，不存在返回空。 */
    @Override
    public PeriodClose findLatestByType(long periodId, PeriodCloseType closeType) {
        return mapper.selectLatestByType(periodId, closeType);
    }

    /** 按原状态与版本迁移状态并写入活动槽、摘要与复核人，成功后同步原对象。 */
    @Override
    public void updateStatus(PeriodClose close, PeriodCloseStatus status, Integer activeSlot, String closeHash, Long checkerId) {
        Instant now = now();
        if (mapper.updateStatus(close, status, activeSlot, closeHash, checkerId, now) != 1) {
            throw new ConcurrentRequestException("关账流程状态已变化: " + close.getId());
        }
        close.setStatus(status);
        close.setActiveSlot(activeSlot);
        close.setCloseHash(closeHash);
        close.setCheckerId(checkerId);
        close.setVersion(close.getVersion() + 1);
        close.setUpdateTime(now);
    }

    /** 读取流程摘要，不存在返回空。 */
    @Override
    public PeriodCloseView findView(long periodId, int closeNo) {
        return mapper.selectView(periodId, closeNo);
    }

    /** 按序号降序分页读取流程摘要。 */
    @Override
    public List<PeriodCloseView> findViewPage(long periodId, int size, int offset) {
        return mapper.selectViewPage(periodId, size, offset);
    }

    /** 统计期间的流程数。 */
    @Override
    public long countByPeriod(long periodId) {
        return mapper.countByPeriod(periodId);
    }

    /** 统计待执行或可重试的补偿任务。 */
    @Override
    public long countPendingCompensationEvents() {
        return mapper.countPendingCompensationEvents();
    }

    /** 统计账簿内余额非零的渠道与银行账户。 */
    @Override
    public long countNonZeroTreasuryAccounts(List<Long> bookIds) {
        return mapper.countNonZeroTreasuryAccounts(bookIds);
    }

    /** 当前 UTC 时间，截断到数据库支持的微秒精度。 */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
