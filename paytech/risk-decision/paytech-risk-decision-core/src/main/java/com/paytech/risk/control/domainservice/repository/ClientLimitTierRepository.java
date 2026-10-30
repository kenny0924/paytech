package com.paytech.risk.control.domainservice.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.paytech.risk.control.core.dal.dataobject.ClientLimitTier;
import com.paytech.risk.control.core.enums.LimitTier;
import com.paytech.risk.control.domain.limittier.LimitTierListFilter;
import com.paytech.risk.control.domain.limittier.LimitTierPlacement;

/** Client 当前档位的单表持久化边界。 */
public interface ClientLimitTierRepository {
    /**
     * 插入建档行
     *
     * @param row 新档位行
     * @return true 已插入；false 该 Client 已有档位（唯一索引冲突，语句级回滚，所在事务仍可继续）
     */
    boolean insertIfAbsent(ClientLimitTier row);

    /** 按 Client 读取档位行。 */
    Optional<ClientLimitTier> findByClientId(long clientId);

    /**
     * 以期望版本把档位行改为批准后的新值
     *
     * @param row 期望版本与档位未变的档位行；成功后同步新值、版本与时间
     * @param next 新值
     *         <li>行数不符抛出版本冲突。</li>
     */
    void moveTo(ClientLimitTier row, LimitTierPlacement next);

    /** 按筛选条件分页读取，更新时间倒序。 */
    List<ClientLimitTier> findPage(LimitTierListFilter filter, int limit, long offset);

    /** 统计同一条件下的档位行数。 */
    long count(LimitTierListFilter filter);

    /** 统计指定档位的 Client 数。 */
    long countByTier(LimitTier tier);

    /** 统计观察期已过目标天数的 OBSERVATION 行数。 */
    long countObservationDue(Instant now);
}
