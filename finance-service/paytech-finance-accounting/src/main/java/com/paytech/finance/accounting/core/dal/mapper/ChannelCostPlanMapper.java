package com.paytech.finance.accounting.core.dal.mapper;

import java.time.Instant;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.finance.accounting.core.dal.dataobject.ChannelCostPlan;

/** t_channel_cost_plan：渠道成本方案持久化。 */
@Mapper
public interface ChannelCostPlanMapper {

    /** 插入方案。 */
    int insert(ChannelCostPlan record);

    /** 按方案代码读取。 */
    ChannelCostPlan selectByCode(@Param("planCode") String planCode);

    /** 按 Channel 读取。 */
    ChannelCostPlan selectByChannelId(@Param("channelId") long channelId);

    /** 按方案代码读取并加排他锁。 */
    ChannelCostPlan selectByCodeForUpdate(@Param("planCode") String planCode);

    /**
     * 以期望版本切换当前版本指针、状态置为 ACTIVE，版本号加一
     *
     * @return 受影响行数
     */
    int updateCurrentVersion(@Param("plan") ChannelCostPlan plan, @Param("currentVerId") long currentVerId,
        @Param("now") Instant now);
}
