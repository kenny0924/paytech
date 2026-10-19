package com.paytech.account.domainservice.repository.impl;

import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.account.api.enums.MovementSourceType;
import com.paytech.account.core.dal.dataobject.BalanceMovement;
import com.paytech.account.core.dal.mapper.BalanceMovementMapper;
import com.paytech.account.domainservice.repository.BalanceMovementRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;

import lombok.AllArgsConstructor;

/** BalanceMovementRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class BalanceMovementRepositoryImpl implements BalanceMovementRepository {
    private final BalanceMovementMapper mapper;

    /** 同一来源的并发首次请求只有一个成功，其余转为可重试的并发冲突。 */
    @Override
    public void insert(BalanceMovement movement) {
        try {
            mapper.insert(movement);
        } catch (DuplicateKeyException e) {
            throw new ConcurrentRequestException("余额桶移动被并发登记: " + movement.getSourceType().getCode() + "/"
                + movement.getSourceRef(), e);
        }
    }

    /** 按来源读取。 */
    @Override
    public BalanceMovement findBySource(MovementSourceType sourceType, String sourceRef) {
        return mapper.selectBySource(sourceType, sourceRef);
    }

    /** 空集合不查询。 */
    @Override
    public List<BalanceMovement> findByIds(List<Long> ids) {
        return ids.isEmpty() ? List.of() : mapper.selectByIds(ids);
    }
}
