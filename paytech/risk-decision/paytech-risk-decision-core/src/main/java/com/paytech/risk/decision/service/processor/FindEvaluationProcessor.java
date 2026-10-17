package com.paytech.risk.decision.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.risk.decision.domain.evaluation.EvaluationView;
import com.paytech.risk.decision.service.processor.request.FindEvaluationRequest;
import com.paytech.risk.decision.service.query.EvaluationReader;

import lombok.AllArgsConstructor;

/**
 * 按风控点与业务身份查询正式评估的结论
 * <li>只读，不开启事务；每个业务身份最多一条正式评估。</li>
 * <li>风控点未登记或业务身份格式不符返回 400，没有评估返回 404。</li>
 */
@Component
@AllArgsConstructor
public class FindEvaluationProcessor extends AbstractProcessor<FindEvaluationRequest, EvaluationView> {
    private final EvaluationReader reader;

    @Override
    protected EvaluationView processRequest(FindEvaluationRequest request) {
        return reader.primary(request.getPointCode(), request.getBusinessKey());
    }
}
