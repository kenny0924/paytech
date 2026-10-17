package com.paytech.risk.decision.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.risk.decision.domain.evaluation.EvaluationView;
import com.paytech.risk.decision.service.processor.request.EvaluationIdRequest;
import com.paytech.risk.decision.service.query.EvaluationReader;

import lombok.AllArgsConstructor;

/**
 * 按评估 ID 查询评估结论
 * <li>只读，不开启事务：评估及其快照、轨迹一次写入后不再修改。</li>
 * <li>评估不存在返回 404。</li>
 */
@Component
@AllArgsConstructor
public class GetEvaluationProcessor extends AbstractProcessor<EvaluationIdRequest, EvaluationView> {
    private final EvaluationReader reader;

    @Override
    protected EvaluationView processRequest(EvaluationIdRequest request) {
        return reader.evaluation(request.getEvaluationId());
    }
}
