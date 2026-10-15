package com.paytech.risk.decision.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.risk.decision.domain.evaluation.EvaluationExplanationView;
import com.paytech.risk.decision.service.processor.request.EvaluationIdRequest;
import com.paytech.risk.decision.service.query.EvaluationReader;

import lombok.AllArgsConstructor;

/**
 * 查询评估解释：冻结输入原值、流程路径、执行过的规则、变量取值与影子比较
 * <li>只读，不开启事务；名称与编码取自评估所用发布的冻结内容，常量与集合为当前值。</li>
 * <li>响应含 IP、卡指纹等受限输入原值，入口路径的 Web 日志只记元数据。</li>
 */
@Component
@AllArgsConstructor
public class GetEvaluationExplanationProcessor extends AbstractProcessor<EvaluationIdRequest, EvaluationExplanationView> {
    private final EvaluationReader reader;

    @Override
    protected EvaluationExplanationView processRequest(EvaluationIdRequest request) {
        return reader.explanation(request.getEvaluationId());
    }
}
