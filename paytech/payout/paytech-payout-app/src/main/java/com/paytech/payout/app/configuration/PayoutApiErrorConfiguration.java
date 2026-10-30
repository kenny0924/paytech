package com.paytech.payout.app.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

import com.paytech.foundation.web.api.ApiErrorStatusResolver;
import com.paytech.payout.api.enums.PayoutErrorCode;

/** payout 合同错误码的 HTTP 状态（technique 8.1）；其余沿用共享异常处理器的默认状态。 */
@Configuration(proxyBeanMethods = false)
public class PayoutApiErrorConfiguration {

    /** 409 / 422 / 403 / 404 按 PayoutErrorCode 映射。 */
    @Bean
    ApiErrorStatusResolver payoutErrorStatusResolver() {
        return (code, fallback) -> code instanceof PayoutErrorCode payout ? switch (payout) {
            case BENEFICIARY_DUPLICATE, BENEFICIARY_STATUS_CONFLICT -> HttpStatus.CONFLICT;
            case BENEFICIARY_UNSUPPORTED_ROUTE -> HttpStatus.UNPROCESSABLE_CONTENT;
            case FOUR_EYES_REQUIRED, BENEFICIARY_REVEAL_FORBIDDEN -> HttpStatus.FORBIDDEN;
            case BENEFICIARY_ACCOUNT_NOT_FOUND, BENEFICIARY_REQUIREMENT_NOT_FOUND -> HttpStatus.NOT_FOUND;
        } : fallback;
    }
}
