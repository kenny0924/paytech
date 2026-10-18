package com.paytech.risk.service.convert;

import java.util.function.Supplier;

import com.paytech.foundation.common.error.FailException;
import com.paytech.risk.decision.api.enums.RiskDecisionConfigurationErrorCode;
import com.paytech.risk.decision.domainservice.service.DictionaryConflictException;

/** 字典领域失败到公开业务 Code 的 Web 边界转换，不拥有业务编排。 */
public final class DecisionDictionaryExceptionConverter {
    /** 异常转换不持有实例状态。 */
    private DecisionDictionaryExceptionConverter() { }

    /** 复用字典错误转换，其他失败原样传播。 */
    public static <T> T mapped(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (DictionaryConflictException failure) {
            throw convert(failure);
        }
    }

    /** 输出已登记的安全业务错误并保留内部失败原因。 */
    public static FailException convert(DictionaryConflictException failure) {
        return new FailException(switch (failure.reason()) {
            case NOT_FOUND -> RiskDecisionConfigurationErrorCode.RISK_DICTIONARY_NOT_FOUND;
            case CODE_EXISTS -> RiskDecisionConfigurationErrorCode.RISK_DICTIONARY_CODE_EXISTS;
            case CHANGED -> RiskDecisionConfigurationErrorCode.RISK_DICTIONARY_CHANGED;
            case SELECTION_UNAVAILABLE -> RiskDecisionConfigurationErrorCode.RISK_DICTIONARY_SELECTION_UNAVAILABLE;
        }, null, failure);
    }
}
