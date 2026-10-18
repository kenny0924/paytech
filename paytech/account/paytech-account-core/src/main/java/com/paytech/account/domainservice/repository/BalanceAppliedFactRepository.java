package com.paytech.account.domainservice.repository;

import java.util.List;

import com.paytech.account.core.dal.dataobject.BalanceAppliedEntry;
import com.paytech.account.core.dal.dataobject.BalanceAppliedFact;
import com.paytech.account.core.enums.AppliedFactStatus;
import com.paytech.account.domain.BookIdentity;

/** t_balance_applied_fact 与 t_balance_applied_entry：已处理经济事实与其余额变化。 */
public interface BalanceAppliedFactRepository {

    /** 插入处理记录；同一 fact_id + fact_version 已被并发登记时以并发冲突失败。 */
    void insert(BalanceAppliedFact fact);

    /** 以排他锁读取一个经济事实的处理记录，不存在返回空；必须在事务内调用。 */
    BalanceAppliedFact lockByFactId(long factId);

    /** 以原状态为条件迁移状态并写入处理信息，成功后同步原对象；状态已变化时以并发冲突失败。 */
    void updateStatus(BalanceAppliedFact fact, AppliedFactStatus status);

    /** 账簿主体内已处理（应用、冲回、跳过）的事实数。 */
    long countProcessed(BookIdentity book);

    /** 账簿主体内已处理事实的最大 factId，没有时为空。 */
    Long maxProcessedFactId(BookIdentity book);

    /** 统计账簿主体内 factId 大于给定值的处理记录（含预登记）。 */
    long countAfter(BookIdentity book, long factId);

    /** 删除经营主体账簿内 factId 不大于给定值的处理记录与余额变化（重建使用）。 */
    void deleteUpTo(BookIdentity book, long factId);

    /** 批量插入余额变化；空集合不写。 */
    void insertEntries(List<BalanceAppliedEntry> entries);

    /** 读取一条已处理事实的全部余额变化。 */
    List<BalanceAppliedEntry> findEntries(long appliedFactId);
}
