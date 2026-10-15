package com.paytech.finance.accounting.domainservice.repository.impl;

import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.finance.accounting.core.dal.dataobject.AdjustmentApproval;
import com.paytech.finance.accounting.core.dal.mapper.AdjustmentApprovalMapper;
import com.paytech.finance.accounting.domainservice.repository.AdjustmentApprovalRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;

import lombok.AllArgsConstructor;

/** AdjustmentApprovalRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class AdjustmentApprovalRepositoryImpl implements AdjustmentApprovalRepository {
    private final AdjustmentApprovalMapper mapper;

    /** 插入审批记录；同一角色已有记录时按并发冲突拒绝。 */
    @Override
    public void insert(AdjustmentApproval approval) {
        insertConcurrent(() -> mapper.insert(approval), "审批记录已存在: 调整 " + approval.getAdjustmentId());
    }

    /** 读取一笔调整的全部审批记录。 */
    @Override
    public List<AdjustmentApproval> findByAdjustment(long adjustmentId) {
        return mapper.selectByAdjustment(adjustmentId);
    }

    /** 执行插入，唯一键冲突说明同一命令已被并发处理。 */
    private void insertConcurrent(Runnable insert, String message) {
        try {
            insert.run();
        } catch (DuplicateKeyException e) {
            throw new ConcurrentRequestException(message, e);
        }
    }
}
