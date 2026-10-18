package com.paytech.billing.domainservice.repository;

import com.paytech.billing.core.dal.dataobject.ChannelCostPlan;

/** t_channel_cost_plan：方案读取、创建与当前版本指针切换。 */
public interface ChannelCostPlanRepository {

    /** 按方案代码读取，不存在返回空。 */
    ChannelCostPlan findByCode(String planCode);

    /** 按 Channel 读取，不存在返回空。 */
    ChannelCostPlan findByChannelId(long channelId);

    /** 按方案代码读取并加排他锁，不存在返回空；必须在调用方事务内。 */
    ChannelCostPlan lockByCode(String planCode);

    /** 写入新方案；Channel 或方案代码重复时按并发冲突拒绝。 */
    void insert(ChannelCostPlan plan);

    /** 以期望版本为条件切换当前版本、状态置为 ACTIVE 并使版本号加一，成功后同步原对象；版本已变化时按并发冲突拒绝。 */
    void switchCurrentVersion(ChannelCostPlan plan, long currentVerId);
}
