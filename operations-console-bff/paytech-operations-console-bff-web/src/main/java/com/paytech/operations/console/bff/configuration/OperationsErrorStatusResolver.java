package com.paytech.operations.console.bff.configuration;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.enums.CodeMessageEnum;
import com.paytech.foundation.web.api.ApiErrorStatusResolver;
import com.paytech.operations.console.bff.domain.enums.OperationsBffErrorCode;
import com.paytech.platform.support.api.enums.GatewayConfigurationErrorCode;
import com.paytech.platform.support.api.enums.OperationsErrorCode;
import com.paytech.risk.api.enums.RiskDecisionConfigurationErrorCode;

/** 仅声明浏览器错误状态；错误体、Request ID 和异常终态处理统一归 Foundation。 @author Codex */
@Component
public final class OperationsErrorStatusResolver implements ApiErrorStatusResolver {

    @Override
    public HttpStatus resolve(CodeMessageEnum errorCode, HttpStatus defaultStatus) {
        if (errorCode == com.paytech.foundation.common.error.CommonErrorCode.NOT_FOUND) return HttpStatus.NOT_FOUND;
        if (errorCode instanceof com.paytech.risk.api.enums.RiskVariableErrorCode variable) {
            return switch (variable) {
                case INVALID_VARIABLE_DEFINITION, INVALID_OPERAND, LEGACY_AUTHORING_NOT_SUPPORTED -> HttpStatus.BAD_REQUEST;
                case SYSTEM_VARIABLE_READ_ONLY -> HttpStatus.FORBIDDEN;
                case VARIABLE_NOT_FOUND -> HttpStatus.NOT_FOUND;
                default -> HttpStatus.CONFLICT;
            };
        }
        if (errorCode instanceof OperationsBffErrorCode browserCode) {
            return switch (browserCode) {
                case ORIGIN_REJECTED -> HttpStatus.FORBIDDEN;
                case UNSUPPORTED_MEDIA_TYPE -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
                case DEPENDENCY_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
                case DEPENDENCY_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            };
        }
        if (errorCode instanceof OperationsErrorCode platformCode) {
            return switch (platformCode) {
                case OPERATIONS_AUTHENTICATION_FAILED, OPERATIONS_SESSION_INVALID -> HttpStatus.UNAUTHORIZED;
                case OPERATIONS_PERMISSION_DENIED -> HttpStatus.FORBIDDEN;
            };
        }
        if (errorCode instanceof GatewayConfigurationErrorCode gatewayCode) {
            return switch (gatewayCode) {
                case GATEWAY_CONFIGURATION_NOT_FOUND -> HttpStatus.NOT_FOUND;
            };
        }
        if (errorCode instanceof RiskDecisionConfigurationErrorCode riskCode) {
            return switch (riskCode) {
                case RISK_DICTIONARY_NOT_FOUND, RISK_DECISION_CONFIGURATION_NOT_FOUND -> HttpStatus.NOT_FOUND;
                case RISK_FLOW_REFERENCE_UNAVAILABLE -> HttpStatus.UNPROCESSABLE_CONTENT;
                case RISK_FLOW_CODE_EXISTS, RISK_FLOW_VERSION_CONFLICT, RISK_FLOW_DELETED, RISK_DICTIONARY_CODE_EXISTS, RISK_DICTIONARY_CHANGED, RISK_DICTIONARY_SELECTION_UNAVAILABLE, RISK_RULE_SET_IN_USE, RISK_RULE_SET_CHANGED, RISK_RULE_SET_DELETED -> HttpStatus.CONFLICT;
            };
        }
        return defaultStatus;
    }
}
