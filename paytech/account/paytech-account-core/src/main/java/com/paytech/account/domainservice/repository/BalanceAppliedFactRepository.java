package com.paytech.account.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.account.core.dal.dataobject.BalanceAppliedFact;
import com.paytech.account.core.enums.AppliedFactStatus;

/** t_balance_applied_fact：已处理经济事实读写。 */
public interface BalanceAppliedFactRepository {

    /** 插入处理记录；同一 fact_id + fact_version 已被并发登记时以并发冲突失败。 */
    void insert(BalanceAppliedFact fact);

    /** 以排他锁读取一个经济事实的处理记录，不存在返回空；必须在事务内调用。 */
    BalanceAppliedFact lockByFactId(long factId);

    /** 读取一组经济事实的处理记录；空集合返回空列表。 */
    List<BalanceAppliedFact> findByFactIds(List<Long> factIds);

    /** 以原状态为条件迁移状态并写入处理信息，成功后同步原对象；状态已变化时以并发冲突失败。 */
    void updateStatus(BalanceAppliedFact fact, AppliedFactStatus status, Instant now);

    /** 经营主体与币种下自身事件已到达的事实数与最大 factId。 */
    Progress operatingEntityProgress(String entityNo, String currency);

    /** Finance Entity 与币种下自身事件已到达的事实数与最大 factId。 */
    Progress financeEntityProgress(long financeEntityId, String currency);

    /** 引用 Finance Entity 的事实所在的全部经营主体编号。 */
    List<String> findEntityNosByFinanceEntity(long financeEntityId, String currency);

    /** 到期待移动的事实，主键大于游标，按主键升序，最多 limit 条。 */
    List<BalanceAppliedFact> findMaturityDue(Instant asOf, long afterId, int limit);

    /** 以 PENDING 为条件标记到期完成，成功后同步原对象；状态已变化时以并发冲突失败。 */
    void markMatured(BalanceAppliedFact fact, Instant maturedTime);

    /**
     * 事实应用进度
     *
     * @param factCount 自身事件已到达的事实数
     * @param maxFactId 其中最大的 factId；没有时为空
     */
    record Progress(long factCount, Long maxFactId) {
    }
}
