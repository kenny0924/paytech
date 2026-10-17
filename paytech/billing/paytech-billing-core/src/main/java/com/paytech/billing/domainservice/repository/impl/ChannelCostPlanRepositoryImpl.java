package com.paytech.billing.domainservice.repository.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.billing.core.dal.dataobject.ChannelCostPlan;
import com.paytech.billing.core.dal.mapper.ChannelCostPlanMapper;
import com.paytech.billing.core.enums.ChannelCostPlanStatus;
import com.paytech.billing.domainservice.repository.ChannelCostPlanRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;

import lombok.AllArgsConstructor;

/** ChannelCostPlanRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class ChannelCostPlanRepositoryImpl implements ChannelCostPlanRepository {
    private final Clock clock;
    private final ChannelCostPlanMapper mapper;

    /** 按方案代码读取，不存在返回空。 */
    @Override
    public ChannelCostPlan findByCode(String planCode) {
        return mapper.selectByCode(planCode);
    }

    /** 按 Channel 读取，不存在返回空。 */
    @Override
    public ChannelCostPlan findByChannelId(long channelId) {
        return mapper.selectByChannelId(channelId);
    }

    /** 按方案代码读取并加排他锁，不存在返回空。 */
    @Override
    public ChannelCostPlan lockByCode(String planCode) {
        return mapper.selectByCodeForUpdate(planCode);
    }

    /** 写入新方案；Channel 或方案代码重复时按并发冲突拒绝。 */
    @Override
    public void insert(ChannelCostPlan plan) {
        try {
            mapper.insert(plan);
        } catch (DuplicateKeyException e) {
            throw new ConcurrentRequestException("渠道成本方案已存在: Channel " + plan.getChannelId() + ", 方案 "
                + plan.getPlanCode(), e);
        }
    }

    /** 以期望版本条件切换当前版本指针并置为 ACTIVE，成功后同步原对象。 */
    @Override
    public void switchCurrentVersion(ChannelCostPlan plan, long currentVerId) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (mapper.updateCurrentVersion(plan, currentVerId, now) != 1) {
            throw new ConcurrentRequestException("渠道成本方案当前版本已变化: " + plan.getPlanCode());
        }
        plan.setCurrentVerId(currentVerId);
        plan.setStatus(ChannelCostPlanStatus.ACTIVE);
        plan.setVersion(plan.getVersion() + 1);
        plan.setUpdateTime(now);
    }
}
