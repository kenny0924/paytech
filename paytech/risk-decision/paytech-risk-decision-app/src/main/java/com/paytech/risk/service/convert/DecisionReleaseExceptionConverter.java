package com.paytech.risk.service.convert;

import java.util.function.Supplier;

import com.paytech.foundation.common.error.FailException;
import com.paytech.risk.decision.api.enums.RiskDecisionConfigurationErrorCode;
import com.paytech.risk.decision.domainservice.service.DecisionConfigurationNotFoundException;
import com.paytech.risk.decision.domainservice.service.RuleSetManagementConflictException;

/** 只在 HTTP 适配层将 Owner 错误转换为现有协议错误码。 */
public final class DecisionReleaseExceptionConverter {
    private DecisionReleaseExceptionConverter() { }

    /** 保留发布接口已登记的不存在与规则管理冲突响应。 */
    public static <T> T mapped(Supplier<T> operation) {
        try { return operation.get(); }
        catch (DecisionConfigurationNotFoundException failure) {
            throw new FailException(RiskDecisionConfigurationErrorCode.RISK_DECISION_CONFIGURATION_NOT_FOUND, null, failure);
        } catch (RuleSetManagementConflictException failure) {
            throw new FailException(switch (failure.reason()) {
                case IN_USE -> RiskDecisionConfigurationErrorCode.RISK_RULE_SET_IN_USE;
                case CHANGED -> RiskDecisionConfigurationErrorCode.RISK_RULE_SET_CHANGED;
                case DELETED -> RiskDecisionConfigurationErrorCode.RISK_RULE_SET_DELETED;
            }, null, failure);
        }
    }
}
