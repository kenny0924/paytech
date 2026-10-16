package com.paytech.risk.decision.domainservice.repository;

import com.paytech.risk.decision.core.dal.dataobject.CheckoutInputSnapshot;
import java.util.Optional;

/** 结账输入快照的单表持久化边界。 */
public interface CheckoutInputSnapshotRepository {
    /**
     * 插入快照
     * <li>同一订单已有评估时抛出并发冲突。</li>
     */
    void insert(CheckoutInputSnapshot row);

    /** 按订单读取。 */
    Optional<CheckoutInputSnapshot> findByOrder(long orderId);

    /** 按正式评估读取。 */
    Optional<CheckoutInputSnapshot> findByEvaluation(long evaluationId);
}
