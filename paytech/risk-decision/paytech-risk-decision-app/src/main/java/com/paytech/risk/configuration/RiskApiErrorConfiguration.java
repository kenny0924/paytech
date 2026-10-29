package com.paytech.risk.configuration;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.web.api.ApiErrorStatusResolver;
import com.paytech.risk.decision.api.enums.RiskDecisionConfigurationErrorCode;
import com.paytech.risk.decision.api.enums.RiskUnderwritingErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

/** Risk API 专有错误到 HTTP 状态的窄映射。 */
@Configuration(proxyBeanMethods = false)
public class RiskApiErrorConfiguration {

    @Bean
    ApiErrorStatusResolver riskApiErrorStatusResolver() {
        return (errorCode, defaultStatus) -> {
            if (errorCode == CommonErrorCode.NOT_FOUND) {
                return HttpStatus.NOT_FOUND;
            }
            if (errorCode instanceof com.paytech.risk.decision.api.enums.RiskVariableErrorCode variable) {
                return switch (variable) {
                    case INVALID_VARIABLE_DEFINITION, INVALID_OPERAND, LEGACY_AUTHORING_NOT_SUPPORTED -> HttpStatus.BAD_REQUEST;
                    case SYSTEM_VARIABLE_READ_ONLY -> HttpStatus.FORBIDDEN;
                    case VARIABLE_NOT_FOUND -> HttpStatus.NOT_FOUND;
                    default -> HttpStatus.CONFLICT;
                };
            }
            if (errorCode instanceof RiskUnderwritingErrorCode underwriting) {
                return underwriting == RiskUnderwritingErrorCode.RISK_UNDERWRITING_NOT_FOUND ? HttpStatus.NOT_FOUND
                    : HttpStatus.CONFLICT;
            }
            if (!(errorCode instanceof RiskDecisionConfigurationErrorCode riskCode)) return defaultStatus;
            return switch (riskCode) {
                case RISK_FLOW_CODE_EXISTS, RISK_FLOW_VERSION_CONFLICT, RISK_FLOW_DELETED -> HttpStatus.CONFLICT;
                case RISK_FLOW_REFERENCE_UNAVAILABLE -> HttpStatus.UNPROCESSABLE_CONTENT;
                case RISK_DICTIONARY_NOT_FOUND, RISK_DECISION_CONFIGURATION_NOT_FOUND -> HttpStatus.NOT_FOUND;
                case RISK_DICTIONARY_CODE_EXISTS, RISK_DICTIONARY_CHANGED, RISK_DICTIONARY_SELECTION_UNAVAILABLE, RISK_RULE_SET_IN_USE, RISK_RULE_SET_CHANGED, RISK_RULE_SET_DELETED -> HttpStatus.CONFLICT;
            };
        };
    }
}
