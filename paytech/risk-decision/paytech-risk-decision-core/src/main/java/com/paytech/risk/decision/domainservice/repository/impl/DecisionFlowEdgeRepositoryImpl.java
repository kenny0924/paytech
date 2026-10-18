package com.paytech.risk.decision.domainservice.repository.impl;

import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.core.dal.dataobject.DecisionFlowEdgeRow;
import com.paytech.risk.decision.core.dal.mapper.DecisionFlowMapper;
import com.paytech.risk.decision.domainservice.repository.DecisionFlowEdgeRepository;

import lombok.AllArgsConstructor;

/** 仅操作所属流程表，保留数据库唯一键、条件更新与影响行数保护。 */
@Repository
@AllArgsConstructor
public class DecisionFlowEdgeRepositoryImpl implements DecisionFlowEdgeRepository {
    private final DecisionFlowMapper mapper;

    /** 按准确版本读取本表全部明细。 */
    @Override
    public List<DecisionFlowEdgeRow> findByVersion(long versionId) { return List.copyOf(mapper.edges(versionId)); }

    /** 唯一键仍是资源锁租约失效后的最终防线，冲突回滚整笔保存。 */
    @Override
    public void insert(DecisionFlowEdgeRow value) {
        try {
            if (mapper.insertDecisionFlowEdge(value) != 1) {
                throw new ErrorException("流程明细写入行数不一致");
            }
        } catch (DuplicateKeyException failure) {
            throw new ConcurrentRequestException("流程配置被并发更新，请刷新后重试", failure);
        }
    }
}
