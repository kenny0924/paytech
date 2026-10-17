package com.paytech.risk.decision.domainservice.service;

import java.util.List;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.risk.decision.domain.runtime.EvaluationInputContract.Violation;

/** 统一评估输入不符合风控点输入合同；入口层转换为带字段错误的 422。 */
public class DecisionInputViolationException extends InvalidParameterException {
    private static final long serialVersionUID = 1L;

    private final List<Violation> violations;

    /** 保存全部字段级违规。 */
    public DecisionInputViolationException(List<Violation> violations) {
        super("评估输入不符合风控点输入合同");
        this.violations = List.copyOf(violations);
    }

    /** 返回全部字段级违规。 */
    public List<Violation> violations() {
        return violations;
    }
}
