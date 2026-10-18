package com.paytech.ledger.domainservice.repository.impl;

import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.ledger.core.dal.dataobject.CloseApproval;
import com.paytech.ledger.core.dal.mapper.CloseApprovalMapper;
import com.paytech.ledger.domainservice.repository.CloseApprovalRepository;

import lombok.AllArgsConstructor;

/** CloseApprovalRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class CloseApprovalRepositoryImpl implements CloseApprovalRepository {
    private final CloseApprovalMapper mapper;

    /** 插入审批记录；同一角色已有记录时按并发冲突拒绝。 */
    @Override
    public void insert(CloseApproval approval) {
        try {
            mapper.insert(approval);
        } catch (DuplicateKeyException e) {
            throw new ConcurrentRequestException("关账审批记录已存在: " + approval.getPeriodCloseId(), e);
        }
    }

    /** 读取流程的审批记录。 */
    @Override
    public List<CloseApproval> findByClose(long periodCloseId) {
        return mapper.selectByClose(periodCloseId);
    }
}
