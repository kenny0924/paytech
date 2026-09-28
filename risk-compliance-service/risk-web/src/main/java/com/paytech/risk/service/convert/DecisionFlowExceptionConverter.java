package com.paytech.risk.service.convert;

import java.util.function.Supplier;

import com.paytech.foundation.common.error.FailException;
import com.paytech.risk.api.enums.RiskDecisionConfigurationErrorCode;
import com.paytech.risk.api.enums.RiskVariableErrorCode;
import com.paytech.risk.decision.domainservice.service.DecisionConfigurationNotFoundException;
import com.paytech.risk.decision.domainservice.service.RiskVariableException;

/** 转换流程依赖的目录与不存在错误，流程定位问题保留给既有 Problem Handler。 */
public final class DecisionFlowExceptionConverter {
    /** 无实例状态的协议异常适配。 */
    private DecisionFlowExceptionConverter() { }

    /** 保留已登记的错误代码和内部原因，不捕获或改写图静态问题。 */
    public static <T> T mapped(Supplier<T> operation) {
        try { return operation.get(); }
        catch (RiskVariableException failure) {
            throw new FailException(RiskVariableErrorCode.valueOf(failure.reason().name()), failure.getMessage(), failure);
        } catch (DecisionConfigurationNotFoundException failure) {
            throw new FailException(RiskDecisionConfigurationErrorCode.RISK_DECISION_CONFIGURATION_NOT_FOUND, null, failure);
        }
    }
}
