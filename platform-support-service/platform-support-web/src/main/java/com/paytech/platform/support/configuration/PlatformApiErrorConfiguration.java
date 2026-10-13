package com.paytech.platform.support.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

import com.paytech.foundation.web.api.ApiErrorStatusResolver;
import com.paytech.platform.support.api.enums.ApiCredentialErrorCode;
import com.paytech.platform.support.api.enums.GatewayConfigurationErrorCode;
import com.paytech.platform.support.api.enums.OperationsErrorCode;
import com.paytech.platform.support.api.enums.StandardApiExecutionErrorCode;

/** Platform Support 公开错误码到 HTTP 状态的最小映射。 */
@Configuration(proxyBeanMethods = false)
public class PlatformApiErrorConfiguration {

    @Bean
    ApiErrorStatusResolver platformApiErrorStatusResolver() {
        return (errorCode, defaultStatus) -> {
            if (errorCode instanceof com.paytech.platform.support.api.enums.OnboardingFileErrorCode fileError) {
                return switch (fileError) {
                    case FILE_NOT_FOUND -> HttpStatus.NOT_FOUND;
                    case FILE_TARGET_CONFLICT, FILE_STATE_CONFLICT -> HttpStatus.CONFLICT;
                    case FILE_POLICY_REJECTED -> HttpStatus.UNPROCESSABLE_CONTENT;
                };
            }
            if (errorCode instanceof StandardApiExecutionErrorCode executionError) {
                return switch (executionError) {
                    case STANDARD_API_NOT_FOUND, INTEGRATION_IMPLEMENTATION_NOT_FOUND -> HttpStatus.NOT_FOUND;
                    case INTEGRATION_CONFIG_INVALID -> HttpStatus.INTERNAL_SERVER_ERROR;
                    case INTEGRATION_INITIALIZATION_FAILED, INTEGRATION_BUDGET_EXHAUSTED -> HttpStatus.SERVICE_UNAVAILABLE;
                };
            }
            if (errorCode == com.paytech.platform.support.api.enums.TenantProjectionErrorCode.TENANT_APPLICANT_INVALID) return HttpStatus.FORBIDDEN;
            if (errorCode == com.paytech.platform.support.api.enums.TenantProjectionErrorCode.TENANT_PROJECTION_NOT_READY) return HttpStatus.SERVICE_UNAVAILABLE;
            if (errorCode == com.paytech.platform.support.api.enums.TenantProjectionErrorCode.TENANT_PROJECTION_CONFLICT
                || errorCode == com.paytech.platform.support.api.enums.TenantProjectionErrorCode.TENANT_PROJECTION_STALE) return HttpStatus.CONFLICT;
            if (errorCode == com.paytech.foundation.common.error.CommonErrorCode.NOT_FOUND) return HttpStatus.NOT_FOUND;
            if (errorCode == com.paytech.platform.support.api.enums.ClientAuthenticationErrorCode.CLIENT_EMAIL_LINK_INVALID) {
                return HttpStatus.UNPROCESSABLE_CONTENT;
            }
            if (errorCode == com.paytech.platform.support.api.enums.ClientAuthenticationErrorCode.CLIENT_AUTHENTICATION_FAILED
                || errorCode == com.paytech.platform.support.api.enums.ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID) {
                return HttpStatus.UNAUTHORIZED;
            }
            if (errorCode == ApiCredentialErrorCode.API_CREDENTIAL_AUTHENTICATION_FAILED) {
                return HttpStatus.UNAUTHORIZED;
            }
            if (errorCode == GatewayConfigurationErrorCode.GATEWAY_CONFIGURATION_NOT_FOUND) {
                return HttpStatus.NOT_FOUND;
            }
            if (errorCode == OperationsErrorCode.OPERATIONS_AUTHENTICATION_FAILED
                || errorCode == OperationsErrorCode.OPERATIONS_SESSION_INVALID) {
                return HttpStatus.UNAUTHORIZED;
            }
            if (errorCode == OperationsErrorCode.OPERATIONS_PERMISSION_DENIED) {
                return HttpStatus.FORBIDDEN;
            }
            return defaultStatus;
        };
    }
}
