package com.paytech.risk.decision.domainservice.repository.impl;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.risk.decision.core.dal.dataobject.CheckoutInputSnapshot;
import com.paytech.risk.decision.core.dal.mapper.CheckoutInputSnapshotMapper;
import com.paytech.risk.decision.domainservice.repository.CheckoutInputSnapshotRepository;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/** 结账输入快照持久化。 */
@Repository
@AllArgsConstructor
public class CheckoutInputSnapshotRepositoryImpl implements CheckoutInputSnapshotRepository {
    private final CheckoutInputSnapshotMapper mapper;

    /** 插入快照；订单唯一冲突转为并发冲突。 */
    @Override
    public void insert(CheckoutInputSnapshot row) {
        try {
            mapper.insert(row);
        } catch (DuplicateKeyException duplicate) {
            throw new ConcurrentRequestException("订单已有结账风控评估: " + row.getOrderId());
        }
    }

    /** 按订单读取。 */
    @Override
    public Optional<CheckoutInputSnapshot> findByOrder(long orderId) {
        return Optional.ofNullable(mapper.selectByOrder(orderId));
    }

    /** 按正式评估读取。 */
    @Override
    public Optional<CheckoutInputSnapshot> findByEvaluation(long evaluationId) {
        return Optional.ofNullable(mapper.selectByEvaluation(evaluationId));
    }
}
