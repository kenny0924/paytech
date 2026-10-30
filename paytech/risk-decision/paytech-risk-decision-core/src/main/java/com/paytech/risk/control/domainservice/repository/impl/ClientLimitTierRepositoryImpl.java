package com.paytech.risk.control.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.risk.control.core.dal.dataobject.ClientLimitTier;
import com.paytech.risk.control.core.dal.mapper.ClientLimitTierMapper;
import com.paytech.risk.control.core.enums.LimitTier;
import com.paytech.risk.control.domain.limittier.LimitTierException;
import com.paytech.risk.control.domain.limittier.LimitTierException.Reason;
import com.paytech.risk.control.domain.limittier.LimitTierListFilter;
import com.paytech.risk.control.domain.limittier.LimitTierPlacement;
import com.paytech.risk.control.domainservice.repository.ClientLimitTierRepository;

import lombok.AllArgsConstructor;

/** Client 当前档位持久化。 */
@Repository
@AllArgsConstructor
public class ClientLimitTierRepositoryImpl implements ClientLimitTierRepository {
    private final ClientLimitTierMapper mapper;

    /** 唯一索引冲突说明并发建档已完成，返回 false 由调用方跳过后续写入。 */
    @Override
    public boolean insertIfAbsent(ClientLimitTier row) {
        try {
            return mapper.insert(row) == 1;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    /** 按 Client 读取档位行。 */
    @Override
    public Optional<ClientLimitTier> findByClientId(long clientId) {
        return Optional.ofNullable(mapper.selectByClientId(clientId));
    }

    /** 条件更新成功后同步原对象；行数不符说明档位已被并发变更。 */
    @Override
    public void moveTo(ClientLimitTier row, LimitTierPlacement next) {
        if (mapper.updateTier(row, next) != 1) {
            throw new LimitTierException(Reason.VERSION_CONFLICT, "Client " + row.getClientId() + " 的档位已变更，请刷新后重试");
        }
        row.setTier(next.getTier());
        row.setPreviousTier(next.getPreviousTier());
        row.setSourceType(next.getSourceType());
        row.setSourceChangeId(next.getSourceChangeId());
        row.setEffectiveTime(next.getEffectiveTime());
        row.setObservationStartedTime(next.getObservationStartedTime());
        row.setObservationTargetDays(next.getObservationTargetDays());
        row.setObservationTargetTransactions(next.getObservationTargetTransactions());
        row.setObservationStartedByChangeId(next.getObservationStartedByChangeId());
        row.setVersion(row.getVersion() + 1);
        row.setUpdateTime(next.getEffectiveTime());
    }

    /** 按筛选条件分页读取。 */
    @Override
    public List<ClientLimitTier> findPage(LimitTierListFilter filter, int limit, long offset) {
        return mapper.selectPage(filter, limit, offset);
    }

    /** 统计同一条件下的档位行数。 */
    @Override
    public long count(LimitTierListFilter filter) {
        return mapper.count(filter);
    }

    /** 统计指定档位的 Client 数。 */
    @Override
    public long countByTier(LimitTier tier) {
        return mapper.countByTier(tier);
    }

    /** 统计观察期已过目标天数的行数。 */
    @Override
    public long countObservationDue(Instant now) {
        return mapper.countObservationDue(now);
    }
}
