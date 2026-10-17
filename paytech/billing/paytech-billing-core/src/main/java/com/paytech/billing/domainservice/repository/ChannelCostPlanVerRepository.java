package com.paytech.billing.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.billing.core.dal.dataobject.ChannelCostPlanVer;
import com.paytech.billing.core.enums.ChannelCostVersionStatus;

/** t_channel_cost_plan_ver：成本版本读取、创建与状态迁移。 */
public interface ChannelCostPlanVerRepository {

    /** 按主键读取，不存在返回空。 */
    ChannelCostPlanVer findById(long id);

    /** 按方案与版本号读取，不存在返回空。 */
    ChannelCostPlanVer findByPlanVer(long planId, int costVer);

    /** 按方案与版本号读取并加排他锁，不存在返回空；必须在调用方事务内。 */
    ChannelCostPlanVer lockByPlanVer(long planId, int costVer);

    /** 方案全部版本，按版本号降序。 */
    List<ChannelCostPlanVer> findByPlan(long planId);

    /** 方案中状态为 ACTIVE 或 INACTIVE 且生效区间 [effective_from, effective_to) 覆盖该时间的版本。 */
    List<ChannelCostPlanVer> findEffectiveAt(long planId, Instant occurredAt);

    /** 方案的下一个版本号（当前最大值 + 1）。 */
    int nextCostVer(long planId);

    /** 方案是否已有 DRAFT 或 PENDING_APPROVAL 版本。 */
    boolean existsOpenVersion(long planId);

    /** 写入新版本；版本号重复时按并发冲突拒绝。 */
    void insert(ChannelCostPlanVer ver);

    /** 以原状态为条件迁移到新状态，成功后同步原对象；状态已变化时按并发冲突拒绝。 */
    void updateStatus(ChannelCostPlanVer ver, ChannelCostVersionStatus status);

    /**
     * 以 PENDING_APPROVAL 为条件写入复核结果，成功后同步原对象；状态已变化时按并发冲突拒绝
     *
     * @param status APPROVED、ACTIVE 或 REJECTED
     */
    void review(ChannelCostPlanVer ver, ChannelCostVersionStatus status, long checkerId);

    /** 以 ACTIVE 为条件把版本置为 INACTIVE 并写入失效时间，成功后同步原对象；状态已变化时按并发冲突拒绝。 */
    void deactivate(ChannelCostPlanVer ver, Instant effectiveTo);
}
