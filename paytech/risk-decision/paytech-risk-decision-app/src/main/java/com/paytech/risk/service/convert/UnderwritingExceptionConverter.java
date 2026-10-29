package com.paytech.risk.service.convert;

import java.util.function.Supplier;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;
import com.paytech.foundation.httpproblem.api.RequestBodyValidationException;
import com.paytech.risk.control.domain.underwriting.UnderwritingException;
import com.paytech.risk.control.domain.underwriting.UnderwritingValidationException;
import com.paytech.risk.decision.api.enums.RiskUnderwritingErrorCode;

/**
 * 承保领域失败到已发布业务 Code 的 HTTP 边界转换
 * <li>字段组合与提交必填转为公共 422 VALIDATION_ERROR 并按 errors[] 定位；其他承保失败的 HTTP 状态由 RiskApiErrorConfiguration 映射。</li>
 */
public final class UnderwritingExceptionConverter {
    /** 无实例状态的协议异常适配。 */
    private UnderwritingExceptionConverter() { }

    /** 执行承保用例并转换承保失败，其他失败原样传播。 */
    public static <T> T mapped(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (UnderwritingValidationException failure) {
            throw new RequestBodyValidationException(failure.issues().stream()
                .map(issue -> new ProblemFieldError(issue.field(), issue.message())).distinct().toList(), failure);
        } catch (UnderwritingException failure) {
            throw new FailException(code(failure.reason()), failure.getMessage(), failure);
        }
    }

    /** 失败原因对应的公开错误码。 */
    static RiskUnderwritingErrorCode code(UnderwritingException.Reason reason) {
        return switch (reason) {
            case NOT_FOUND -> RiskUnderwritingErrorCode.RISK_UNDERWRITING_NOT_FOUND;
            case OPEN_EXISTS -> RiskUnderwritingErrorCode.RISK_UNDERWRITING_OPEN_EXISTS;
            case VERSION_CONFLICT -> RiskUnderwritingErrorCode.RISK_UNDERWRITING_VERSION_CONFLICT;
            case STATE_INVALID -> RiskUnderwritingErrorCode.RISK_UNDERWRITING_STATE_INVALID;
            case SELF_REVIEW -> RiskUnderwritingErrorCode.RISK_UNDERWRITING_SELF_REVIEW;
            case DECLINE_REQUIRES_REVIEW -> RiskUnderwritingErrorCode.RISK_UNDERWRITING_DECLINE_REQUIRES_REVIEW;
            case AUTO_NOT_ELIGIBLE -> RiskUnderwritingErrorCode.RISK_UNDERWRITING_AUTO_NOT_ELIGIBLE;
        };
    }
}
