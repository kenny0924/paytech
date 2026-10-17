package com.paytech.ledger.domainservice.repository.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.ledger.core.dal.dataobject.ManualAdjustment;
import com.paytech.ledger.core.dal.mapper.ManualAdjustmentMapper;
import com.paytech.ledger.core.enums.AdjustmentStatus;
import com.paytech.ledger.domainservice.repository.ManualAdjustmentRepository;

import lombok.AllArgsConstructor;

/** ManualAdjustmentRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class ManualAdjustmentRepositoryImpl implements ManualAdjustmentRepository {
    private final Clock clock;
    private final ManualAdjustmentMapper mapper;

    /** 插入人工调整头。 */
    @Override
    public void insert(ManualAdjustment adjustment) {
        mapper.insert(adjustment);
    }

    /** 按主键读取调整，不存在返回空。 */
    @Override
    public ManualAdjustment findById(long id) {
        return mapper.selectById(id);
    }

    /** 按原状态与版本迁移调整状态，成功后同步原对象。 */
    @Override
    public void updateStatus(ManualAdjustment adjustment, AdjustmentStatus status) {
        Instant now = now();
        if (mapper.updateStatus(adjustment, status, now) != 1) {
            throw new ConcurrentRequestException("人工调整状态已变化: " + adjustment.getId());
        }
        adjustment.setStatus(status);
        adjustment.setVersion(adjustment.getVersion() + 1);
        adjustment.setUpdateTime(now);
    }

    /** 当前 UTC 时间，截断到数据库支持的微秒精度。 */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
