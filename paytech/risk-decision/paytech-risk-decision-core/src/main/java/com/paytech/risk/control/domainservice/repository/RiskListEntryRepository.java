package com.paytech.risk.control.domainservice.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.paytech.risk.control.core.dal.dataobject.RiskListEntry;
import com.paytech.risk.control.core.enums.RiskListEntryStatus;
import com.paytech.risk.control.domain.list.RiskSubjectType;

/** 风险名单成员的单表持久化边界。 */
public interface RiskListEntryRepository {
    /**
     * 插入成员
     *
     * @param row 已构造的成员
     *         <li>同名单同主体已有生效成员时抛出并发冲突。</li>
     */
    void insert(RiskListEntry row);

    /** 按 ID 读取成员。 */
    Optional<RiskListEntry> findById(long entryId);

    /** 读取同名单同主体当前占用生效槽位的成员。 */
    Optional<RiskListEntry> findActive(long listId, RiskSubjectType subjectType, String subjectKey);

    /**
     * 结束成员生效区间，释放生效槽位
     *
     * @param row 期望仍为 ACTIVE 且版本未变的成员；成功后同步状态、区间、版本与时间
     * @param status 结束后的状态，RELEASED 或 EXPIRED
     * @param deactivatedBy 人工失效人，过期结束时为空
     * @param deactivatedReason 失效原因，过期结束时为空
     * @param effectiveTo 生效终点，不晚于原终点
     * @param now 本次更新时间
     */
    void close(RiskListEntry row, RiskListEntryStatus status, Long deactivatedBy, String deactivatedReason,
        Instant effectiveTo, Instant now);

    /** 指定时刻主体是否处于名单成员的生效区间内。 */
    boolean isEffective(long listId, RiskSubjectType subjectType, String subjectKey, Instant at);

    /** 按条件分页读取成员，新成员在前。 */
    List<RiskListEntry> findPage(long listId, String subjectKey, RiskListEntryStatus status, int limit, long offset);

    /** 统计同一条件下的成员数量。 */
    long count(long listId, String subjectKey, RiskListEntryStatus status);
}
