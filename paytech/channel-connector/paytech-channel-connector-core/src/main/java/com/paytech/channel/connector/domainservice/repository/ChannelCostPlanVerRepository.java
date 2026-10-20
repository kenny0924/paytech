package com.paytech.channel.connector.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.channel.connector.core.dal.dataobject.ChannelCostPlanVer;
import com.paytech.channel.connector.core.enums.ChannelCostVersionStatus;
import com.paytech.channel.connector.domain.ChannelCostVersionContentCount;

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

    /** CC-B 补充：方案全部版本的适用范围数与成本组成数，一次查询。 */
    List<ChannelCostVersionContentCount> findContentCountsByPlan(long planId);

    /** 方案中状态为 ACTIVE 或 INACTIVE 且生效区间 [effective_from, effective_to) 覆盖该时间的版本，按版本号升序。 */
    List<ChannelCostPlanVer> findEffectiveAt(long planId, Instant occurredAt);

    /** 全部方案中 APPROVED 且 effective_from 不晚于给定时间的版本，按方案、生效时间、版本号升序，至多 limit 条。 */
    List<ChannelCostPlanVer> findDueApproved(Instant asOf, int limit);

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

    /** 以 APPROVED 为条件把版本置为 ACTIVE，成功后同步原对象；状态已变化时按并发冲突拒绝。 */
    void activate(ChannelCostPlanVer ver);

    /**
     * 作废未到生效时间的 APPROVED 版本：置为 INACTIVE 且失效时间等于生效时间，成功后同步原对象
     * <li>条件更新 status = APPROVED 且 effective_from 晚于 now；行数不为 1 时按并发冲突拒绝。</li>
     */
    void cancel(ChannelCostPlanVer ver, Instant now);

    /** 以 ACTIVE 为条件把版本置为 INACTIVE 并写入失效时间，成功后同步原对象；状态已变化时按并发冲突拒绝。 */
    void deactivate(ChannelCostPlanVer ver, Instant effectiveTo);
}
