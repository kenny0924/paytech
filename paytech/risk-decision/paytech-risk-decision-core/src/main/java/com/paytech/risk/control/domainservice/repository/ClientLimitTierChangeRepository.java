package com.paytech.risk.control.domainservice.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.paytech.risk.control.core.dal.dataobject.ClientLimitTierChange;
import com.paytech.risk.control.core.enums.LimitTierChangeStatus;

/** 档位变更记录的单表持久化边界。 */
public interface ClientLimitTierChangeRepository {
    /**
     * 插入变更记录
     *
     * @param row 新记录
     *         <li>同一 Client 已有待审批申请（待审批槽位冲突）时抛出 PENDING_EXISTS。</li>
     */
    void insert(ClientLimitTierChange row);

    /** 按 ID 读取。 */
    Optional<ClientLimitTierChange> findById(long changeId);

    /** 按 ID 批量读取；空集合不访问数据库。 */
    List<ClientLimitTierChange> findByIds(List<Long> changeIds);

    /** 读取 Client 的待审批申请。 */
    Optional<ClientLimitTierChange> findPending(long clientId);

    /** 批量读取多个 Client 的待审批申请；空集合不访问数据库。 */
    List<ClientLimitTierChange> findPendingByClientIds(List<Long> clientIds);

    /** 读取 Client 的变更历史，申请时间倒序。 */
    List<ClientLimitTierChange> findHistory(long clientId, int limit);

    /**
     * 结束待审批申请（批准、驳回或撤回），释放待审批槽位
     *
     * @param row 期望仍为 PENDING 且版本未变的申请；成功后同步状态、决定人、时间与版本
     * @param status 结束状态
     * @param decidedBy 审批、驳回或撤回人
     * @param comment 意见；可空
     * @param now 处理时间
     *         <li>行数不符抛出版本冲突。</li>
     */
    void close(ClientLimitTierChange row, LimitTierChangeStatus status, long decidedBy, String comment, Instant now);

    /** 统计待审批申请数。 */
    long countPending();
}
