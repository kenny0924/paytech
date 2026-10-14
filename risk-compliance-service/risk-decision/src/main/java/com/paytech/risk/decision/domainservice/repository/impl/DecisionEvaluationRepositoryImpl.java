package com.paytech.risk.decision.domainservice.repository.impl;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.risk.decision.core.dal.dataobject.DecisionEvaluation;
import com.paytech.risk.decision.core.dal.mapper.DecisionEvaluationMapper;
import com.paytech.risk.decision.domainservice.repository.DecisionEvaluationRepository;

import lombok.AllArgsConstructor;

/** 决策评估持久化。 */
@Repository
@AllArgsConstructor
public class DecisionEvaluationRepositoryImpl implements DecisionEvaluationRepository {
    private final DecisionEvaluationMapper mapper;

    /** 插入评估；唯一身份冲突转为并发冲突。 */
    @Override
    public void insert(DecisionEvaluation row) {
        try {
            mapper.insert(row);
        } catch (DuplicateKeyException duplicate) {
            throw new ConcurrentRequestException("评估请求已存在: " + row.getEvaluationRequestId());
        }
    }

    /** 按 ID 读取。 */
    @Override
    public Optional<DecisionEvaluation> findById(long evaluationId) {
        return Optional.ofNullable(mapper.selectById(evaluationId));
    }

    /** 读取某发布最近的成功正式评估，新评估在前。 */
    @Override
    public List<DecisionEvaluation> findRecentPrimary(long releaseId, int limit) {
        return mapper.selectRecentPrimary(releaseId, limit);
    }

    /** 读取一个影子批次的全部样本评估。 */
    @Override
    public List<DecisionEvaluation> findByShadowRun(long shadowRunId) {
        return mapper.selectByShadowRun(shadowRunId);
    }
}
