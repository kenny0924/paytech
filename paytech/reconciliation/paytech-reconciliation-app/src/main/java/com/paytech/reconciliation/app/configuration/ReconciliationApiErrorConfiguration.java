package com.paytech.reconciliation.app.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

import com.paytech.foundation.web.api.ApiErrorStatusResolver;
import com.paytech.reconciliation.api.enums.ReconciliationErrorCode;

/** reconciliation 合同错误码的 HTTP 状态：按主键读取对象不存在为 404，其余沿用共享异常处理器的默认状态（400）。 */
@Configuration(proxyBeanMethods = false)
public class ReconciliationApiErrorConfiguration {

    /** BANK_STATEMENT_NOT_FOUND、BANK_STATEMENT_LINE_NOT_FOUND → 404。 */
    @Bean
    ApiErrorStatusResolver reconciliationErrorStatusResolver() {
        return (code, fallback) -> code instanceof ReconciliationErrorCode reconciliation ? switch (reconciliation) {
            case BANK_STATEMENT_NOT_FOUND, BANK_STATEMENT_LINE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CLEARING_ACCOUNT_NOT_RESOLVED -> fallback;
        } : fallback;
    }
}
