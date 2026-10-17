package com.paytech.risk.service.convert;

import java.util.List;
import java.util.function.Supplier;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;
import com.paytech.foundation.httpproblem.api.RequestBodyValidationException;
import com.paytech.risk.api.enums.RiskDecisionConfigurationErrorCode;
import com.paytech.risk.api.enums.RiskVariableErrorCode;
import com.paytech.risk.decision.domainservice.service.DecisionConfigurationNotFoundException;
import com.paytech.risk.decision.domainservice.service.DecisionFlowConfigurationException;
import com.paytech.risk.decision.domainservice.service.RiskVariableException;

/**
 * 流程配置领域失败到已登记业务 Code 的 HTTP 边界转换
 * <li>图校验失败转为公共 422 VALIDATION_ERROR 并按 errors[] 定位；其他流程失败的 HTTP 状态由 RiskApiErrorConfiguration 映射。</li>
 */
public final class DecisionFlowExceptionConverter {
    /** 单次响应最多返回的字段定位数。 */
    static final int MAX_FIELD_ERRORS = 100;

    /** 无实例状态的协议异常适配。 */
    private DecisionFlowExceptionConverter() { }

    /** 执行 Owner 用例并转换已登记的流程、变量与不存在错误，其他失败原样传播。 */
    public static <T> T mapped(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (DecisionFlowConfigurationException failure) {
            throw convert(failure);
        } catch (RiskVariableException failure) {
            throw new FailException(RiskVariableErrorCode.valueOf(failure.reason().name()), failure.getMessage(), failure);
        } catch (DecisionConfigurationNotFoundException failure) {
            throw new FailException(RiskDecisionConfigurationErrorCode.RISK_DECISION_CONFIGURATION_NOT_FOUND, null, failure);
        }
    }

    /** 按失败原因选择公共字段校验错误或流程业务 Code，保留原始失败。 */
    static FailException convert(DecisionFlowConfigurationException failure) {
        return switch (failure.reason()) {
            case INVALID_GRAPH -> new RequestBodyValidationException(fieldErrors(failure), failure);
            case CODE_EXISTS -> flowFailure(RiskDecisionConfigurationErrorCode.RISK_FLOW_CODE_EXISTS, failure);
            case VERSION_CONFLICT -> flowFailure(RiskDecisionConfigurationErrorCode.RISK_FLOW_VERSION_CONFLICT, failure);
            case DELETED -> flowFailure(RiskDecisionConfigurationErrorCode.RISK_FLOW_DELETED, failure);
            case REFERENCE_UNAVAILABLE -> flowFailure(RiskDecisionConfigurationErrorCode.RISK_FLOW_REFERENCE_UNAVAILABLE, failure);
        };
    }

    /** 业务 Code 加原始安全文案。 */
    static FailException flowFailure(RiskDecisionConfigurationErrorCode code, DecisionFlowConfigurationException failure) {
        return new FailException(code, failure.getMessage(), failure);
    }

    /** 只投影字段与文案均非空的问题，去重并限制数量；空白项会使公共异常拒绝构造。 */
    static List<ProblemFieldError> fieldErrors(DecisionFlowConfigurationException failure) {
        return failure.issues().stream()
            .filter(issue -> issue.field() != null && !issue.field().isBlank()
                && issue.message() != null && !issue.message().isBlank())
            .map(issue -> new ProblemFieldError(issue.field(), issue.message())).distinct().limit(MAX_FIELD_ERRORS).toList();
    }
}
