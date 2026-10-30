package com.paytech.risk.control.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.risk.control.core.dal.dataobject.ClientLimitTierChange;
import com.paytech.risk.control.core.enums.LimitTierChangeStatus;

/** 档位变更记录表映射。 */
public interface ClientLimitTierChangeMapper {
    /** 插入变更记录；同一 Client 已有待审批申请时违反唯一索引。 */
    int insert(@Param("row") ClientLimitTierChange row);

    /** 按 ID 读取。 */
    ClientLimitTierChange selectById(@Param("changeId") long changeId);

    /** 按 ID 批量读取。 */
    List<ClientLimitTierChange> selectByIds(@Param("changeIds") List<Long> changeIds);

    /** 读取 Client 的待审批申请。 */
    ClientLimitTierChange selectPending(@Param("clientId") long clientId);

    /** 批量读取多个 Client 的待审批申请。 */
    List<ClientLimitTierChange> selectPendingByClientIds(@Param("clientIds") List<Long> clientIds);

    /** 读取 Client 的变更历史，申请时间倒序。 */
    List<ClientLimitTierChange> selectHistory(@Param("clientId") long clientId, @Param("limit") int limit);

    /** 以期望版本结束待审批申请，释放待审批槽位。 */
    int close(@Param("row") ClientLimitTierChange row, @Param("status") LimitTierChangeStatus status,
        @Param("decidedBy") long decidedBy, @Param("comment") String comment, @Param("now") Instant now);

    /** 统计待审批申请数。 */
    long countPending();
}
