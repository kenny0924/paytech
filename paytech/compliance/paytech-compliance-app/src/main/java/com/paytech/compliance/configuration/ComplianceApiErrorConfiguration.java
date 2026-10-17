package com.paytech.compliance.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

import com.paytech.compliance.core.enums.ClientReviewErrorCode;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.web.api.ApiErrorStatusResolver;

/** compliance API 专有错误到 HTTP 状态的窄映射（由原 RiskApiErrorConfiguration 的审核分支拆出，状态不变）。 */
@Configuration(proxyBeanMethods = false)
public class ComplianceApiErrorConfiguration {

    @Bean
    ApiErrorStatusResolver complianceApiErrorStatusResolver() {
        return (errorCode, defaultStatus) -> {
            if (errorCode == CommonErrorCode.NOT_FOUND) {
                return HttpStatus.NOT_FOUND;
            }
            if (!(errorCode instanceof ClientReviewErrorCode review)) {
                return defaultStatus;
            }
            return switch (review) {
                case CLIENT_REVIEW_NOT_FOUND -> HttpStatus.NOT_FOUND;
                case CLIENT_REVIEW_NOT_READY -> HttpStatus.UNPROCESSABLE_CONTENT;
                default -> HttpStatus.CONFLICT;
            };
        };
    }
}
