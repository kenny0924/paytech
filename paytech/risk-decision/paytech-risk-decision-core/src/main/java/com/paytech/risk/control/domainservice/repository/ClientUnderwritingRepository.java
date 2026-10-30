package com.paytech.risk.control.domainservice.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.paytech.risk.control.core.dal.dataobject.ClientUnderwriting;
import com.paytech.risk.control.domain.underwriting.UnderwritingContent;
import com.paytech.risk.control.domain.underwriting.UnderwritingListFilter;
import com.paytech.risk.control.domain.underwriting.UnderwritingStatistics;
import com.paytech.risk.control.domain.underwriting.UnderwritingVerdict;

/** Client 承保版本的单表持久化边界；条件更新成功后同步传入的原版本行。 */
public interface ClientUnderwritingRepository {
    /**
     * 插入版本行
     *
     * @param row 已构造的版本行
     *         <li>进行中槽位冲突抛 OPEN_EXISTS；版本号或有效槽位冲突抛 VERSION_CONFLICT。</li>
     */
    void insert(ClientUnderwriting row);

    /** 按 ID 读取版本行。 */
    Optional<ClientUnderwriting> findById(long underwritingId);

    /** 读取 Client 的进行中版本（DRAFT / SUBMITTED）。 */
    Optional<ClientUnderwriting> findOpen(long clientId);

    /** 读取 Client 的有效 APPROVED 版本。 */
    Optional<ClientUnderwriting> findEffective(long clientId);

    /** 批量读取多个 Client 的有效 APPROVED 版本（REQ-04 档位列表取 CRR）；空集合不访问数据库。 */
    List<ClientUnderwriting> findEffectiveByClientIds(List<Long> clientIds);

    /** 读取 Client 版本号最大的版本。 */
    Optional<ClientUnderwriting> findLatest(long clientId);

    /** 读取 Client 的版本历史，版本号倒序，最多 limit 个。 */
    List<ClientUnderwriting> findHistory(long clientId, int limit);

    /** 按筛选条件分页读取，更新时间倒序。 */
    List<ClientUnderwriting> findPage(UnderwritingListFilter filter, int limit, long offset);

    /** 统计同一筛选条件下的版本数。 */
    long count(UnderwritingListFilter filter);

    /**
     * 列表顶部统计
     *
     * @param monthStart 本月 UTC 起点（含）
     * @param monthEnd 下月 UTC 起点（不含）
     * @return 五项计数
     */
    UnderwritingStatistics statistics(Instant monthStart, Instant monthEnd);

    /**
     * 以期望版本整体替换草稿内容
     *
     * @param row 期望仍为 DRAFT 且版本未变的版本行；成功后同步内容、版本与时间
     * @param content 已校验的新内容
     * @param now 本次更新时间
     */
    void updateDraft(ClientUnderwriting row, UnderwritingContent content, Instant now);

    /**
     * 冻结草稿内容并置为 SUBMITTED（HIGH 风险等待复核）
     *
     * @param row 期望仍为 DRAFT 且版本未变的版本行；成功后同步内容、状态、提交人与时间
     * @param content 已校验的提交内容
     * @param submittedBy 提交人账号 ID
     * @param now 提交时间
     */
    void markSubmitted(ClientUnderwriting row, UnderwritingContent content, long submittedBy, Instant now);

    /**
     * 写入 APPROVED / DECLINED 决定，释放进行中槽位，批准时占用有效槽位
     *
     * @param row 期望状态（DRAFT 或 SUBMITTED）与版本未变的版本行；成功后同步决定事实
     * @param content 同时冻结的提交内容；复核时为空，内容保持提交时的值
     * @param verdict 决定事实
     */
    void decide(ClientUnderwriting row, UnderwritingContent content, UnderwritingVerdict verdict);

    /**
     * 把有效 APPROVED 置为 SUPERSEDED 并释放有效槽位
     *
     * @param row 期望仍为有效 APPROVED 且版本未变的版本行；成功后同步状态、槽位、版本与时间
     * @param now 替代时间
     */
    void supersede(ClientUnderwriting row, Instant now);
}
