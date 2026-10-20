package com.paytech.channel.connector.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.channel.connector.core.dal.dataobject.ChannelCostPlan;
import com.paytech.channel.connector.core.enums.ChannelCostPlanStatus;
import com.paytech.channel.connector.domain.ChannelCostPlanSummaryView;

/** t_channel_cost_plan：渠道成本方案 SQL 参数绑定，SQL 仅位于 XML。 */
public interface ChannelCostPlanMapper {

    /** 插入方案。 */
    int insert(ChannelCostPlan record);

    /** 按主键读取。 */
    ChannelCostPlan selectById(@Param("id") long id);

    /** 按方案代码读取。 */
    ChannelCostPlan selectByCode(@Param("planCode") String planCode);

    /** 按 Channel 读取。 */
    ChannelCostPlan selectByChannelId(@Param("channelId") long channelId);

    /** 按方案代码读取并加排他锁。 */
    ChannelCostPlan selectByCodeForUpdate(@Param("planCode") String planCode);

    /** 按可空的 Channel 与状态筛选方案列表项，按更新时间、主键降序分页。 */
    List<ChannelCostPlanSummaryView> selectSummaries(@Param("channelId") Long channelId,
        @Param("status") ChannelCostPlanStatus status, @Param("offset") int offset, @Param("limit") int limit);

    /** 统计相同筛选条件下的方案数。 */
    long countSummaries(@Param("channelId") Long channelId, @Param("status") ChannelCostPlanStatus status);

    /**
     * 以期望版本切换当前版本指针、状态置为 ACTIVE，版本号加一
     *
     * @return 受影响行数
     */
    int updateCurrentVersion(@Param("plan") ChannelCostPlan plan, @Param("currentVerId") long currentVerId,
        @Param("now") Instant now);
}
