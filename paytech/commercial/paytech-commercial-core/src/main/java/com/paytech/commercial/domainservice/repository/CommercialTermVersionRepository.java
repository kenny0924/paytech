package com.paytech.commercial.domainservice.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import com.paytech.commercial.core.dal.dataobject.CheckoutRecords;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.domain.CommercialCheckoutScope;
import com.paytech.commercial.domain.CommercialTermVersionListQuery;
import com.paytech.commercial.domain.enums.CommercialTermVersionStatus;

/** t_commercial_term_version 的本地仓储；不负责其他表的加载或事务。 */
public interface CommercialTermVersionRepository {
    /** 读取本 Client Checkout 范围（Client 及 client-master 返回的 OE 与 Local Partner）内的 commercial_term_version 记录，保留历史和未生效事实供业务判断。 */
    List<CheckoutRecords.CommercialTermVersion> findForCheckout(CommercialCheckoutScope scope);

    /**
     * 插入商业条款版本；影响行数不为 1 时抛出系统异常，唯一键冲突原样抛出。
     * <li>创建与更新时间截断到微秒后写回对象。</li>
     *
     * @param commercialTermVersion 已准备 ID 与全部字段的新行
     */
    void insert(CommercialTermVersion commercialTermVersion);

    /**
     * 按主键读取。
     *
     * @param termVersionId 版本 ID
     * @return 商业条款版本，不存在为 null
     */
    CommercialTermVersion findById(long termVersionId);

    /**
     * 按唯一键（方案、版本号）读取。
     *
     * @param termPlanId 方案 ID
     * @param versionNo 版本号
     * @return 商业条款版本，不存在为 null
     */
    CommercialTermVersion findByPlanAndVersionNo(long termPlanId, long versionNo);

    /**
     * 读取一个方案的全部版本，按版本号升序。
     *
     * @param termPlanId 方案 ID
     * @return 按 version_no 升序的商业条款版本
     */
    List<CommercialTermVersion> findByPlanId(long termPlanId);

    /**
     * 方案内当前最大版本号，供服务端递增新版本号；并发由方案锁与 uk_comm_term_ver 保护。
     *
     * @param termPlanId 方案 ID
     * @return 最大版本号，没有版本时为 null
     */
    Long findMaxVersionNo(long termPlanId);

    /**
     * 按商业时点选取方案下唯一适用版本（technique.md §2.3）：状态 ∈ {SCHEDULED, ACTIVE, SUPERSEDED} 且 effective_from ≤ asOf < effective_to（空为无穷）中 effective_from 最大的一条；无则 null。
     *
     * @param termPlanId 方案 ID
     * @param asOf 商业时点
     * @return 适用版本，没有时为 null
     */
    CommercialTermVersion findEffectiveAt(long termPlanId, Instant asOf);

    /**
     * 读取生效起点已到的 SCHEDULED 版本，供激活作业逐个调用激活用例。
     *
     * @param now 当前时间
     * @param limit 本轮最多条数
     * @return 按生效起点、版本 ID 升序的版本
     */
    List<CommercialTermVersion> findScheduledDue(Instant now, int limit);

    /**
     * 以对象当前状态为条件迁移状态并写入 active_slot（ACTIVE 为 1，其他为 null）；未命中抛出并发冲突，成功后同步原对象。
     * <li>切换生效版本时先让旧 ACTIVE 释放槽位，再占用新槽位；uk_comm_term_active 兜底。</li>
     *
     * @param version 已读取的版本
     * @param status 目标状态
     * @param activeSlot 1 或 null
     * @param now 本次更新时间
     */
    void updateStatus(CommercialTermVersion version, CommercialTermVersionStatus status, Integer activeSlot, Instant now);

    /**
     * 改写生效区间（排期写起点、被替代时写终点）；以对象当前状态与 update_time 为条件，未命中抛出并发冲突，成功后同步原对象。
     *
     * @param version 已读取的版本
     * @param effectiveFrom 生效起点（含）
     * @param effectiveTo 生效终点（不含），可空
     * @param now 本次更新时间
     */
    void updateEffectivePeriod(CommercialTermVersion version, Instant effectiveFrom, Instant effectiveTo, Instant now);

    /**
     * 按列表条件读取一页版本，排序见 {@link CommercialTermVersionListQuery#order()}。
     *
     * @param query 已校验的列表条件与分页
     * @return 当前页版本
     */
    List<CommercialTermVersion> findPage(CommercialTermVersionListQuery query);

    /**
     * 相同列表条件下的版本总数。
     *
     * @param query 列表条件
     * @return 总数
     */
    long count(CommercialTermVersionListQuery query);

    /**
     * 读取一组方案下指定状态的版本；任一集合为空不访问数据库。
     *
     * @param termPlanIds 方案 ID
     * @param statuses 版本状态
     * @return 按 term_plan_id、version_no 升序
     */
    List<CommercialTermVersion> findByPlanIdsAndStatuses(Collection<Long> termPlanIds,
        Collection<CommercialTermVersionStatus> statuses);

    /**
     * DRAFT 版本首次从模板补齐时写入来源模板版本；成功后同步原对象。
     *
     * @param version 已读取的版本
     * @param sourceTemplateVersionId 模板版本 ID
     * @param now 本次更新时间
     */
    void updateSourceTemplate(CommercialTermVersion version, long sourceTemplateVersionId, Instant now);

    /**
     * 按一组主键读取；空集合不访问数据库。
     *
     * @param termVersionIds 版本 ID
     * @return 按 term_version_id 升序
     */
    List<CommercialTermVersion> findByIds(Collection<Long> termVersionIds);
}
