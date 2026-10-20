package com.paytech.acquiring.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

import com.paytech.acquiring.api.client.v1.enums.CommerceCheckoutErrorCode;
import com.paytech.counterparty.api.enums.CounterpartyErrorCode;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.web.api.ApiErrorStatusResolver;

/** acquiring 对外稳定错误码的 HTTP 状态映射。 */
@Configuration(proxyBeanMethods = false)
public class CommerceApiErrorConfiguration {

    @Bean
    ApiErrorStatusResolver commerceApiErrorStatusResolver() {
        return (errorCode, defaultStatus) -> {
            if (errorCode == CommonErrorCode.VALIDATION_ERROR) {
                return HttpStatus.UNPROCESSABLE_CONTENT;
            }
            // 建单前拦截个人原值引用（规则来自 counterparty-api）：仍按原 Client API 合同返回 422。
            if (errorCode == CounterpartyErrorCode.INVALID_CONSUMER_REFERENCE) {
                return HttpStatus.UNPROCESSABLE_CONTENT;
            }
            if (errorCode instanceof com.paytech.acquiring.api.checkout.v1.enums.CustomerCheckoutErrorCode customerError) {
                return HttpStatus.valueOf(customerError.httpStatus());
            }
            if (!(errorCode instanceof CommerceCheckoutErrorCode checkoutErrorCode)) {
                return defaultStatus;
            }
            return switch (checkoutErrorCode) {
                case INVALID_CURSOR -> HttpStatus.BAD_REQUEST;
                case ORDER_NOT_FOUND, REFUND_NOT_FOUND -> HttpStatus.NOT_FOUND;
                case IDEMPOTENCY_REQUEST_IN_PROGRESS,
                    CLIENT_ORDER_ID_ALREADY_EXISTS, REVERSAL_REQUIRED, ORDER_NOT_CANCELLABLE, CLIENT_REFUND_ID_CONFLICT -> HttpStatus.CONFLICT;
                case CHECKOUT_NOT_ELIGIBLE, CLIENT_CONFIGURATION_NOT_READY, REFUND_AMOUNT_EXCEEDS_REFUNDABLE ->
                    HttpStatus.UNPROCESSABLE_CONTENT;
                case UPSTREAM_PROTOCOL_ERROR -> HttpStatus.BAD_GATEWAY;
                case DEPENDENCY_UNAVAILABLE, CHECKOUT_CREATION_IN_PROGRESS -> HttpStatus.SERVICE_UNAVAILABLE;
                case DEPENDENCY_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            };
        };
    }
}
