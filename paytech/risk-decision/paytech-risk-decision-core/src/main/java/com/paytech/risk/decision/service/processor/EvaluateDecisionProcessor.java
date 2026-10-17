package com.paytech.risk.decision.service.processor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.risk.decision.domain.runtime.EvaluationInputContract;
import com.paytech.risk.decision.domain.runtime.EvaluationInputContract.Violation;
import com.paytech.risk.decision.domainservice.service.DecisionInputViolationException;
import com.paytech.risk.decision.service.evaluation.DecisionPointEvaluator;
import com.paytech.risk.decision.service.processor.request.EvaluateDecisionRequest;
import com.paytech.risk.decision.service.processor.response.EvaluateDecisionResult;

/**
 * 同步风控点的统一评估
 * <li>按风控点编码与合同版本找到已登记的评估器；未登记返回参数错误，输入不符合合同返回字段级违规。</li>
 * <li>按“风控点 + 业务身份”立即尝试加锁，同一业务身份的并发评估只有一个进入；重放、执行与保存由评估器负责。</li>
 */
@Component
public class EvaluateDecisionProcessor extends AbstractLockProcessor<EvaluateDecisionRequest, EvaluateDecisionResult> {
    private final Map<String, DecisionPointEvaluator> evaluators = new HashMap<>();

    /** 按“风控点:合同版本”登记全部评估器；重复登记属于工程错误。 */
    public EvaluateDecisionProcessor(List<DecisionPointEvaluator> evaluators) {
        for (DecisionPointEvaluator evaluator : evaluators) {
            EvaluationInputContract contract = evaluator.contract();
            if (this.evaluators.put(key(contract.pointCode(), contract.contractVersion()), evaluator) != null) {
                throw new IllegalStateException("风控点评估器重复: " + contract.pointCode());
            }
        }
    }

    /** 风控点与合同版本必须已登记，业务身份与输入必须符合合同。 */
    @Override
    protected void validate(EvaluateDecisionRequest request) {
        DecisionPointEvaluator evaluator = evaluator(request);
        List<Violation> violations = evaluator.contract().violations(request.getBusinessKey(), inputs(request));
        if (!violations.isEmpty()) {
            throw new DecisionInputViolationException(violations);
        }
    }

    /** 锁住风控点内的业务身份。 */
    @Override
    protected LockCommand lockCommand(EvaluateDecisionRequest request) {
        return LockCommand.tryLocks(List.of("risk:decision:" + request.getPointCode() + ":" + request.getBusinessKey()),
            DecisionResourceLocks.WAIT_TIME, DecisionResourceLocks.LEASE_TIME);
    }

    @Override
    protected EvaluateDecisionResult processWithinLock(EvaluateDecisionRequest request) {
        return evaluator(request).evaluate(request.getBusinessKey(), inputs(request));
    }

    /** 已登记的评估器；未登记视为参数错误。 */
    DecisionPointEvaluator evaluator(EvaluateDecisionRequest request) {
        DecisionPointEvaluator evaluator = evaluators.get(key(request.getPointCode(), request.getContractVersion()));
        if (evaluator == null) {
            throw new InvalidParameterException("风控点或输入合同版本未登记: " + request.getPointCode());
        }
        return evaluator;
    }

    /** 去掉空值的输入，空值等同于未提供。 */
    static Map<String, String> inputs(EvaluateDecisionRequest request) {
        Map<String, String> result = new HashMap<>();
        request.getInputs().forEach((key, value) -> {
            if (value != null) {
                result.put(key, value);
            }
        });
        return result;
    }

    /** 评估器登记键。 */
    static String key(String pointCode, int contractVersion) {
        return pointCode + ":" + contractVersion;
    }
}
