package com.paytech.billing.app.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

import com.paytech.ledger.api.LedgerSetupQueryApi;
import com.paytech.ledger.api.enums.LedgerSetupErrorCode;
import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;

/**
 * ledger 账务基础资料查询接口使用 Foundation 公共出站边界
 * <li>billing 注册 {@link LedgerSetupQueryApi}，只调用 {@code GET /finance-entities/v1/by-party/{legalPartyId}}（Partner 订单的平台签约 Party
 * 映射 Finance Entity）；其余查询不使用。基址由 spring.http.serviceclient.ledger-service.base-url 直接配置。</li>
 * <li>注册 FINANCE_ENTITY_NOT_FOUND，使 404 解码为明确失败。</li>
 */
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = LedgerClientConfiguration.SERVICE_ID, types = LedgerSetupQueryApi.class)
public class LedgerClientConfiguration {
    /** 服务组名。 */
    public static final String SERVICE_ID = "ledger-service";

    /** 禁止自动重试和重定向；登记计费事实前事务外读取，失败由消息退避重投，不在客户端重试。 */
    @Bean
    RestClientHttpServiceGroupConfigurer ledgerClient() {
        return HttpServiceClientFactory.configureGroup(SERVICE_ID,
            HttpServiceClientOptions.strict().withStrictJsonResponses(8192), LedgerSetupErrorCode.FINANCE_ENTITY_NOT_FOUND);
    }
}
