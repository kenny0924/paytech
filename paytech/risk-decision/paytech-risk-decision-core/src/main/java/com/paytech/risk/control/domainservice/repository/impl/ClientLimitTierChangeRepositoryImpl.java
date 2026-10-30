package com.paytech.risk.control.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.risk.control.core.dal.dataobject.ClientLimitTierChange;
import com.paytech.risk.control.core.dal.mapper.ClientLimitTierChangeMapper;
import com.paytech.risk.control.core.enums.LimitTierChangeStatus;
import com.paytech.risk.control.domain.limittier.LimitTierException;
import com.paytech.risk.control.domain.limittier.LimitTierException.Reason;
import com.paytech.risk.control.domainservice.repository.ClientLimitTierChangeRepository;

import lombok.AllArgsConstructor;

/** 档位变更记录持久化。 */
@Repository
@AllArgsConstructor
public class ClientLimitTierChangeRepositoryImpl implements ClientLimitTierChangeRepository {
    private final ClientLimitTierChangeMapper mapper;

    /** 插入记录；待审批槽位冲突转为已有待审批申请。 */
    @Override
    public void insert(ClientLimitTierChange row) {
        try {
            mapper.insert(row);
        } catch (DuplicateKeyException duplicate) {
            throw new LimitTierException(Reason.PENDING_EXISTS, "Client " + row.getClientId() + " 已有待审批的档位变更申请");
        }
    }

    /** 按 ID 读取。 */
    @Override
    public Optional<ClientLimitTierChange> findById(long changeId) {
        return Optional.ofNullable(mapper.selectById(changeId));
    }

    /** 按 ID 批量读取。 */
    @Override
    public List<ClientLimitTierChange> findByIds(List<Long> changeIds) {
        return changeIds.isEmpty() ? List.of() : mapper.selectByIds(changeIds);
    }

    /** 读取 Client 的待审批申请。 */
    @Override
    public Optional<ClientLimitTierChange> findPending(long clientId) {
        return Optional.ofNullable(mapper.selectPending(clientId));
    }

    /** 批量读取待审批申请。 */
    @Override
    public List<ClientLimitTierChange> findPendingByClientIds(List<Long> clientIds) {
        return clientIds.isEmpty() ? List.of() : mapper.selectPendingByClientIds(clientIds);
    }

    /** 读取变更历史。 */
    @Override
    public List<ClientLimitTierChange> findHistory(long clientId, int limit) {
        return mapper.selectHistory(clientId, limit);
    }

    /** 条件更新成功后同步原对象；行数不符说明申请已被并发处理。 */
    @Override
    public void close(ClientLimitTierChange row, LimitTierChangeStatus status, long decidedBy, String comment,
        Instant now) {
        if (mapper.close(row, status, decidedBy, comment, now) != 1) {
            throw new LimitTierException(Reason.VERSION_CONFLICT, "档位变更申请已被处理: " + row.getChangeId());
        }
        row.setStatus(status);
        row.setPendingSlot(null);
        row.setDecidedBy(decidedBy);
        row.setDecidedTime(now);
        row.setDecisionComment(comment);
        row.setVersion(row.getVersion() + 1);
        row.setUpdateTime(now);
    }

    /** 统计待审批申请数。 */
    @Override
    public long countPending() {
        return mapper.countPending();
    }
}
