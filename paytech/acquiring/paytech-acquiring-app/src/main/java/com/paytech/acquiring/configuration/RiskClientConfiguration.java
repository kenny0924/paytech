package com.paytech.commerce.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;
import com.paytech.risk.api.evaluation.RiskDecisionApi;

/** Risk 统一评估接口使用服务发现、负载均衡和 Foundation 公共出站边界。 */
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = RiskClientConfiguration.SERVICE_ID, types = RiskDecisionApi.class)
public class RiskClientConfiguration {
    /** Risk 服务组名，同时是服务发现中的服务 ID。 */
    public static final String SERVICE_ID = "risk-service";

    /** 复用公共错误与 Request ID 处理，禁止自动重试和重定向；重试次数由结账风控端口控制。 */
    @Bean
    RestClientHttpServiceGroupConfigurer riskClient() {
        return HttpServiceClientFactory.configureGroup(SERVICE_ID,
            HttpServiceClientOptions.strict().withStrictJsonResponses(8192));
    }
}
