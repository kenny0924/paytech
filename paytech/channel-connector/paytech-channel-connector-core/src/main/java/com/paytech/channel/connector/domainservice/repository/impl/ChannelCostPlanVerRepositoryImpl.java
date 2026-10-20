package com.paytech.channel.connector.domainservice.repository.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.channel.connector.core.dal.dataobject.ChannelCostPlanVer;
import com.paytech.channel.connector.core.dal.mapper.ChannelCostPlanVerMapper;
import com.paytech.channel.connector.core.enums.ChannelCostVersionStatus;
import com.paytech.channel.connector.domain.ChannelCostVersionContentCount;
import com.paytech.channel.connector.domainservice.repository.ChannelCostPlanVerRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;

import lombok.AllArgsConstructor;

/** ChannelCostPlanVerRepository 的 MyBatis 实现；不建立事务。 */
@Repository
@AllArgsConstructor
public class ChannelCostPlanVerRepositoryImpl implements ChannelCostPlanVerRepository {
    private final Clock clock;
    private final ChannelCostPlanVerMapper mapper;

    /** 按主键读取，不存在返回空。 */
    @Override
    public ChannelCostPlanVer findById(long id) {
        return mapper.selectById(id);
    }

    /** 按方案与版本号读取，不存在返回空。 */
    @Override
    public ChannelCostPlanVer findByPlanVer(long planId, int costVer) {
        return mapper.selectByPlanVer(planId, costVer);
    }

    /** 按方案与版本号读取并加排他锁，不存在返回空。 */
    @Override
    public ChannelCostPlanVer lockByPlanVer(long planId, int costVer) {
        return mapper.selectByPlanVerForUpdate(planId, costVer);
    }

    /** 方案全部版本，按版本号降序。 */
    @Override
    public List<ChannelCostPlanVer> findByPlan(long planId) {
        return mapper.selectByPlan(planId);
    }

    /** CC-B 补充：方案全部版本的适用范围数与成本组成数。 */
    @Override
    public List<ChannelCostVersionContentCount> findContentCountsByPlan(long planId) {
        return mapper.selectContentCountsByPlan(planId);
    }

    /** 方案中 ACTIVE 或 INACTIVE 且 [effective_from, effective_to) 覆盖该时间的版本，按版本号升序。 */
    @Override
    public List<ChannelCostPlanVer> findEffectiveAt(long planId, Instant occurredAt) {
        return mapper.selectEffectiveAt(planId, occurredAt);
    }

    /** 全部方案中已到生效时间的 APPROVED 版本，按方案、生效时间、版本号升序。 */
    @Override
    public List<ChannelCostPlanVer> findDueApproved(Instant asOf, int limit) {
        return mapper.selectDueApproved(asOf, limit);
    }

    /** 方案的下一个版本号。 */
    @Override
    public int nextCostVer(long planId) {
        return mapper.selectNextCostVer(planId);
    }

    /** 方案是否已有 DRAFT 或 PENDING_APPROVAL 版本。 */
    @Override
    public boolean existsOpenVersion(long planId) {
        return mapper.countOpenVersions(planId) > 0;
    }

    /** 写入新版本；版本号重复时按并发冲突拒绝。 */
    @Override
    public void insert(ChannelCostPlanVer ver) {
        try {
            mapper.insert(ver);
        } catch (DuplicateKeyException e) {
            throw new ConcurrentRequestException("渠道成本版本号已存在: 方案 " + ver.getPlanId() + ", 版本 "
                + ver.getCostVer(), e);
        }
    }

    /** 以原状态为条件迁移状态，成功后同步原对象。 */
    @Override
    public void updateStatus(ChannelCostPlanVer ver, ChannelCostVersionStatus status) {
        transit(ver, status, ver.getCheckerId(), ver.getEffectiveTo());
    }

    /** 以 PENDING_APPROVAL 为条件写入复核结果，成功后同步原对象。 */
    @Override
    public void review(ChannelCostPlanVer ver, ChannelCostVersionStatus status, long checkerId) {
        requireStatus(ver, ChannelCostVersionStatus.PENDING_APPROVAL);
        transit(ver, status, checkerId, ver.getEffectiveTo());
    }

    /** 以 APPROVED 为条件置为 ACTIVE，复核人与失效时间不变，成功后同步原对象。 */
    @Override
    public void activate(ChannelCostPlanVer ver) {
        requireStatus(ver, ChannelCostVersionStatus.APPROVED);
        transit(ver, ChannelCostVersionStatus.ACTIVE, ver.getCheckerId(), ver.getEffectiveTo());
    }

    /** 以 ACTIVE 为条件置为 INACTIVE 并写入失效时间，成功后同步原对象。 */
    @Override
    public void deactivate(ChannelCostPlanVer ver, Instant effectiveTo) {
        requireStatus(ver, ChannelCostVersionStatus.ACTIVE);
        transit(ver, ChannelCostVersionStatus.INACTIVE, ver.getCheckerId(), effectiveTo);
    }

    /** 条件作废 APPROVED 版本，成功后同步状态、失效时间与更新时间。 */
    @Override
    public void cancel(ChannelCostPlanVer ver, Instant now) {
        if (mapper.cancelApproved(ver, now) != 1) {
            throw new ConcurrentRequestException("渠道成本版本状态或生效时间已变化，无法作废: " + ver.getId());
        }
        ver.setStatus(ChannelCostVersionStatus.INACTIVE);
        ver.setEffectiveTo(ver.getEffectiveFrom());
        ver.setUpdateTime(now);
    }

    /** 以对象当前状态为条件执行迁移，行数不为 1 时按并发冲突拒绝，成功后同步原对象。 */
    void transit(ChannelCostPlanVer ver, ChannelCostVersionStatus status, Long checkerId, Instant effectiveTo) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (mapper.updateStatus(ver, status, checkerId, effectiveTo, now) != 1) {
            throw new ConcurrentRequestException("渠道成本版本状态已变化: " + ver.getId());
        }
        ver.setStatus(status);
        ver.setCheckerId(checkerId);
        ver.setEffectiveTo(effectiveTo);
        ver.setUpdateTime(now);
    }

    /** 迁移前对象状态必须为期望的原状态。 */
    void requireStatus(ChannelCostPlanVer ver, ChannelCostVersionStatus expected) {
        if (ver.getStatus() != expected) {
            throw new ConcurrentRequestException("渠道成本版本状态已变化: " + ver.getId() + ", 状态 "
                + ver.getStatus().getCode());
        }
    }
}
