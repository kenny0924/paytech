package com.paytech.counterparty.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

import com.paytech.counterparty.api.enums.CounterpartyErrorCode;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.web.api.ApiErrorStatusResolver;

/** counterparty 对外稳定错误码的 HTTP 状态映射；与原 Commerce Client API 的 Customer 部分一致。 */
@Configuration(proxyBeanMethods = false)
public class CounterpartyApiErrorConfiguration {

    @Bean
    ApiErrorStatusResolver counterpartyApiErrorStatusResolver() {
        return (errorCode, defaultStatus) -> {
            if (errorCode == CommonErrorCode.VALIDATION_ERROR) {
                return HttpStatus.UNPROCESSABLE_CONTENT;
            }
            if (!(errorCode instanceof CounterpartyErrorCode counterpartyErrorCode)) {
                return defaultStatus;
            }
            return switch (counterpartyErrorCode) {
                case CUSTOMER_NOT_FOUND, STORED_CARD_NOT_FOUND -> HttpStatus.NOT_FOUND;
                case INVALID_CONSUMER_REFERENCE -> HttpStatus.UNPROCESSABLE_CONTENT;
                case UPSTREAM_PROTOCOL_ERROR -> HttpStatus.BAD_GATEWAY;
            };
        };
    }
}
