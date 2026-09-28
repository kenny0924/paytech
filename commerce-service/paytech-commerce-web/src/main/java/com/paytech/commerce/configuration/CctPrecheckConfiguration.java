package com.paytech.commerce.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

import com.paytech.client.contract.api.checkout.CheckoutEligibilityApi;
import com.paytech.client.contract.api.enums.CheckoutErrorCode;
import com.paytech.commerce.client.api.v1.enums.CommerceCheckoutErrorCode;
import com.paytech.commerce.integration.cct.impl.CctPrecheckClientImpl;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;

/** 注册 CCT 合同及其响应、错误策略；公共出站行为由 Foundation 统一处理。 */
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = "client-contract-service", types = CheckoutEligibilityApi.class)
@Import(CctPrecheckClientImpl.class)
public class CctPrecheckConfiguration {
    @Bean
    RestClientHttpServiceGroupConfigurer cctPrecheckClient() {
        var options = HttpServiceClientOptions.strict().withStrictJsonResponses(8192)
            .withDependencyFailureMapper((kind, cause) -> switch (kind) {
                case TIMEOUT -> new ErrorException(CommerceCheckoutErrorCode.DEPENDENCY_TIMEOUT, null, cause);
                case UNAVAILABLE -> new ServiceUnavailableException(CommerceCheckoutErrorCode.DEPENDENCY_UNAVAILABLE, cause);
            });
        return HttpServiceClientFactory.configureGroup("client-contract-service", options, CheckoutErrorCode.values());
    }
}
