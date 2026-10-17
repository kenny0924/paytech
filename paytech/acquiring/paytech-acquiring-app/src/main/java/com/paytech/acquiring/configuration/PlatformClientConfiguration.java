package com.paytech.commerce.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;
import com.paytech.platform.support.api.webhook.WebhookEventApi;

/** Platform Webhook 事件受理接口使用服务发现、负载均衡和 Foundation 公共出站边界。 */
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = PlatformClientConfiguration.SERVICE_ID, types = WebhookEventApi.class)
public class PlatformClientConfiguration {
    /** Platform 服务组名，同时是服务发现中的服务 ID。 */
    public static final String SERVICE_ID = "platform-support-service";

    /**
     * 复用公共错误与 Request ID 处理，禁止自动重试和重定向；重试由补偿任务按执行策略调度。
     * <li>显式接收合规 422：Platform 合同规定 400/422 不重试，冻结内容原样重试也不会通过字段校验。</li>
     */
    @Bean
    RestClientHttpServiceGroupConfigurer platformClient() {
        return HttpServiceClientFactory.configureGroup(SERVICE_ID,
            HttpServiceClientOptions.strict().withStrictJsonResponses(8192).withRequestValidationFailures());
    }
}
