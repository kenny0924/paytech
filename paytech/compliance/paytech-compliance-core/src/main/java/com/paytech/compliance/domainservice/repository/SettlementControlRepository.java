package com.paytech.compliance.domainservice.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;

/** t_settlement_control 的只读访问；结算控制的创建与释放尚未实现。 */
public interface SettlementControlRepository {
    /**
     * 给定 Client 中存在生效中结算冻结的 Client ID
     * <li>生效中：SETTLEMENT_BLOCK、ACTIVE，且 effective_from ≤ now &lt; effective_to（或 effective_to 为空）。</li>
     *
     * @param clientIds 要检查的 Client ID；空集合返回空集合
     * @param now 查询时间
     * @return 被冻结的 Client ID
     */
    Set<Long> findBlockedClientIds(Collection<Long> clientIds, Instant now);
}
