package com.paytech.billing.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.billing.core.dal.dataobject.ChannelCostPlanVer;
import com.paytech.billing.core.enums.ChannelCostVersionStatus;

/** t_channel_cost_plan_ver：渠道成本版本持久化。 */
@Mapper
public interface ChannelCostPlanVerMapper {

    /** 插入版本。 */
    int insert(ChannelCostPlanVer record);

    /** 按主键读取。 */
    ChannelCostPlanVer selectById(@Param("id") long id);

    /** 按方案与版本号读取。 */
    ChannelCostPlanVer selectByPlanVer(@Param("planId") long planId, @Param("costVer") int costVer);

    /** 按方案与版本号读取并加排他锁。 */
    ChannelCostPlanVer selectByPlanVerForUpdate(@Param("planId") long planId, @Param("costVer") int costVer);

    /** 方案全部版本，按版本号降序。 */
    List<ChannelCostPlanVer> selectByPlan(@Param("planId") long planId);

    /** 方案中 ACTIVE 或 INACTIVE 且生效区间覆盖该时间的版本，按版本号升序。 */
    List<ChannelCostPlanVer> selectEffectiveAt(@Param("planId") long planId, @Param("occurredAt") Instant occurredAt);

    /** 方案的下一个版本号。 */
    int selectNextCostVer(@Param("planId") long planId);

    /** 统计方案中 DRAFT 或 PENDING_APPROVAL 版本数。 */
    long countOpenVersions(@Param("planId") long planId);

    /**
     * 以原状态为条件迁移状态，同时写入复核人与失效时间
     *
     * @return 受影响行数
     */
    int updateStatus(@Param("ver") ChannelCostPlanVer ver, @Param("status") ChannelCostVersionStatus status,
        @Param("checkerId") Long checkerId, @Param("effectiveTo") Instant effectiveTo, @Param("now") Instant now);
}
