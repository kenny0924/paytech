package com.paytech.channel.connector.domainservice.repository;

import com.paytech.channel.connector.core.dal.dataobject.ChannelCostPlan;
import com.paytech.channel.connector.core.enums.ChannelCostPlanStatus;
import com.paytech.channel.connector.domain.ChannelCostPlanSummaryView;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

/** t_channel_cost_plan：方案读取、创建与当前版本指针切换。 */
public interface ChannelCostPlanRepository {

    /** 按主键读取，不存在返回空。 */
    ChannelCostPlan findById(long id);

    /** 按方案代码读取，不存在返回空。 */
    ChannelCostPlan findByCode(String planCode);

    /** 按 Channel 读取，不存在返回空。 */
    ChannelCostPlan findByChannelId(long channelId);

    /** 按方案代码读取并加排他锁，不存在返回空；必须在调用方事务内。 */
    ChannelCostPlan lockByCode(String planCode);

    /**
     * 分页读取方案列表项（同库联查 Channel 编码与名称、当前版本与待复核版本数），按更新时间、主键降序
     *
     * @param channelId 可空 Channel 筛选
     * @param status 可空方案状态筛选
     */
    PagingResponseBody<ChannelCostPlanSummaryView> findPage(Long channelId, ChannelCostPlanStatus status,
        PagingRequestBody paging);

    /** 写入新方案；Channel 或方案代码重复时按并发冲突拒绝。 */
    void insert(ChannelCostPlan plan);

    /** 以期望版本为条件切换当前版本、状态置为 ACTIVE 并使版本号加一，成功后同步原对象；版本已变化时按并发冲突拒绝。 */
    void switchCurrentVersion(ChannelCostPlan plan, long currentVerId);
}
