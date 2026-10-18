package com.paytech.risk.decision.domainservice.repository.impl;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.risk.decision.core.dal.dataobject.TransactionRiskAssessment;
import com.paytech.risk.decision.core.dal.mapper.TransactionRiskAssessmentMapper;
import com.paytech.risk.decision.domainservice.repository.TransactionRiskAssessmentRepository;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/** 交易风险评估快照持久化。 */
@Repository
@AllArgsConstructor
public class TransactionRiskAssessmentRepositoryImpl implements TransactionRiskAssessmentRepository {
    private final TransactionRiskAssessmentMapper mapper;

    /** 插入快照；支付尝试唯一冲突转为并发冲突。 */
    @Override
    public void insert(TransactionRiskAssessment row) {
        try {
            mapper.insert(row);
        } catch (DuplicateKeyException duplicate) {
            throw new ConcurrentRequestException("支付尝试已有风控评估: " + row.getPaymentAttemptId());
        }
    }

    /** 按支付尝试读取。 */
    @Override
    public Optional<TransactionRiskAssessment> findByAttempt(long paymentAttemptId) {
        return Optional.ofNullable(mapper.selectByAttempt(paymentAttemptId));
    }

    /** 按正式评估读取。 */
    @Override
    public Optional<TransactionRiskAssessment> findByEvaluation(long evaluationId) {
        return Optional.ofNullable(mapper.selectByEvaluation(evaluationId));
    }
}
