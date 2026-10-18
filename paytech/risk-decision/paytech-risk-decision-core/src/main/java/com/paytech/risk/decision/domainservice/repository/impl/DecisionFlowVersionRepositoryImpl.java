package com.paytech.risk.decision.domainservice.repository.impl;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.core.dal.dataobject.DecisionFlowVersionRow;
import com.paytech.risk.decision.core.dal.mapper.DecisionFlowMapper;
import com.paytech.risk.decision.domainservice.repository.DecisionFlowVersionRepository;

import lombok.AllArgsConstructor;

/** 仅操作所属流程表，保留数据库唯一键、条件更新与影响行数保护。 */
@Repository
@AllArgsConstructor
public class DecisionFlowVersionRepositoryImpl implements DecisionFlowVersionRepository {
    private final DecisionFlowMapper mapper;

    /** 按原主键读取本表事实。 */
    @Override
    public Optional<DecisionFlowVersionRow> findById(long id) { return Optional.ofNullable(mapper.findVersion(id)); }

    /** 唯一键仍是资源锁租约失效后的最终防线，冲突回滚整笔保存。 */
    @Override
    public void insert(DecisionFlowVersionRow value) {
        try {
            if (mapper.insertDecisionFlowVersion(value) != 1) {
                throw new ErrorException("流程明细写入行数不一致");
            }
        } catch (DuplicateKeyException failure) {
            throw new ConcurrentRequestException("流程配置被并发更新，请刷新后重试", failure);
        }
    }

    /** 空页不执行 IN 空集合查询。 */
    @Override
    public List<DecisionFlowVersionRow> findByIds(List<Long> ids) {
        return ids.isEmpty() ? List.of() : List.copyOf(mapper.findVersions(ids));
    }

    /** 复用准确版本表的稳定倒序分页。 */
    @Override
    public List<DecisionFlowVersionRow> history(long flowId, int maxVersionNo, long offset, int limit) {
        return List.copyOf(mapper.history(flowId, maxVersionNo, offset, limit));
    }

    /** 统计同一固定上界内的不可变版本。 */
    @Override
    public long historyCount(long flowId, int maxVersionNo) { return mapper.countHistory(flowId, maxVersionNo); }
}
