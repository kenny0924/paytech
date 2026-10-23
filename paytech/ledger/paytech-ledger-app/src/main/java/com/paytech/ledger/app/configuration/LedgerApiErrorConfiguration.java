package com.paytech.ledger.app.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

import com.paytech.foundation.web.api.ApiErrorStatusResolver;
import com.paytech.ledger.api.enums.LedgerSetupErrorCode;

/** ledger 合同错误码的 HTTP 状态：按 Party 解析财务主体未命中为 404，其余沿用共享异常处理器的默认状态（400）。 */
@Configuration(proxyBeanMethods = false)
public class LedgerApiErrorConfiguration {

    /** FINANCE_ENTITY_NOT_FOUND → 404。 */
    @Bean
    ApiErrorStatusResolver ledgerErrorStatusResolver() {
        return (code, fallback) -> code == LedgerSetupErrorCode.FINANCE_ENTITY_NOT_FOUND ? HttpStatus.NOT_FOUND
            : fallback;
    }
}
