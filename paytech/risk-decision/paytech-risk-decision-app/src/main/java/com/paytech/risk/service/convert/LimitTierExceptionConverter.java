package com.paytech.risk.service.convert;

import java.util.List;
import java.util.function.Supplier;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;
import com.paytech.foundation.httpproblem.api.RequestBodyValidationException;
import com.paytech.risk.control.domain.limittier.LimitTierException;
import com.paytech.risk.control.domain.limittier.LimitTierValidationException;
import com.paytech.risk.decision.api.enums.RiskLimitTierErrorCode;

/**
 * 限额档位领域失败到已发布业务 Code 的 HTTP 边界转换
 * <li>字段组合失败转为公共 422 VALIDATION_ERROR 并按 errors[] 定位；其他档位失败的 HTTP 状态由 RiskApiErrorConfiguration 映射。</li>
 */
public final class LimitTierExceptionConverter {
    /** 无实例状态的协议异常适配。 */
    private LimitTierExceptionConverter() { }

    /** 执行档位用例并转换档位失败，其他失败原样传播。 */
    public static <T> T mapped(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (LimitTierValidationException failure) {
            throw new RequestBodyValidationException(List.of(new ProblemFieldError(failure.field(), failure.getMessage())),
                failure);
        } catch (LimitTierException failure) {
            throw new FailException(code(failure.reason()), failure.getMessage(), failure);
        }
    }

    /** 失败原因对应的公开错误码。 */
    static RiskLimitTierErrorCode code(LimitTierException.Reason reason) {
        return switch (reason) {
            case NOT_FOUND -> RiskLimitTierErrorCode.RISK_LIMIT_TIER_NOT_FOUND;
            case CHANGE_NOT_FOUND -> RiskLimitTierErrorCode.RISK_LIMIT_TIER_CHANGE_NOT_FOUND;
            case TRANSITION_INVALID -> RiskLimitTierErrorCode.RISK_LIMIT_TIER_TRANSITION_INVALID;
            case PENDING_EXISTS -> RiskLimitTierErrorCode.RISK_LIMIT_TIER_CHANGE_PENDING_EXISTS;
            case VERSION_CONFLICT -> RiskLimitTierErrorCode.RISK_LIMIT_TIER_VERSION_CONFLICT;
            case CHECKLIST_NOT_SATISFIED -> RiskLimitTierErrorCode.RISK_LIMIT_TIER_CHECKLIST_NOT_SATISFIED;
            case ELEVATION_REQUIRED -> RiskLimitTierErrorCode.RISK_LIMIT_TIER_ELEVATION_REQUIRED;
            case SELF_APPROVAL -> RiskLimitTierErrorCode.RISK_LIMIT_TIER_SELF_APPROVAL;
            case NOT_REQUESTER -> RiskLimitTierErrorCode.RISK_LIMIT_TIER_NOT_REQUESTER;
            case CHANGE_STATE_INVALID -> RiskLimitTierErrorCode.RISK_LIMIT_TIER_CHANGE_STATE_INVALID;
            case MIGRATION_FORBIDDEN -> RiskLimitTierErrorCode.RISK_LIMIT_TIER_MIGRATION_FORBIDDEN;
        };
    }
}
