package com.paytech.billing.app.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

import com.paytech.reconciliation.api.clearing.ClearingAccountResolutionApi;
import com.paytech.reconciliation.api.enums.ReconciliationErrorCode;
import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;

/**
 * reconciliation 清算账户解析接口使用 Foundation 公共出站边界
 * <li>billing 只注册 {@link ClearingAccountResolutionApi}：登记自动 PAYMENT 事实时事务外调用 {@code POST /reconciliation/v1/clearing-accounts/resolutions}；
 * 基址由 spring.http.serviceclient.reconciliation-service.base-url 直接配置。</li>
 * <li>注册 CLEARING_ACCOUNT_NOT_RESOLVED，使 400 解码为明确失败。</li>
 */
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = ReconciliationClientConfiguration.SERVICE_ID, types = ClearingAccountResolutionApi.class)
public class ReconciliationClientConfiguration {
    /** 服务组名。 */
    public static final String SERVICE_ID = "reconciliation-service";

    /** 禁止自动重试和重定向；登记计费事实前事务外读取，失败由消息退避重投，不在客户端重试。 */
    @Bean
    RestClientHttpServiceGroupConfigurer reconciliationClient() {
        return HttpServiceClientFactory.configureGroup(SERVICE_ID,
            HttpServiceClientOptions.strict().withStrictJsonResponses(8192), ReconciliationErrorCode.CLEARING_ACCOUNT_NOT_RESOLVED);
    }
}
