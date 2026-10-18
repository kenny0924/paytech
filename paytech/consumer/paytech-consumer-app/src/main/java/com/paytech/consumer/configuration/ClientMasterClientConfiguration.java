package com.paytech.consumer.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

import com.paytech.client.master.api.storedcard.ClientStoredCardSettingApi;
import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;

/** client-master 的 Client 已存卡开关只读接口（D42，第 04 步合同）使用服务发现与 Foundation 公共出站边界。 */
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = ClientMasterClientConfiguration.SERVICE_ID, types = ClientStoredCardSettingApi.class)
public class ClientMasterClientConfiguration {
    /** client-master 服务组名，同时是服务发现中的服务 ID。 */
    public static final String SERVICE_ID = "client-master-service";

    /** 禁止自动重试和重定向；失败由资格判断按 SETTING_UNAVAILABLE 关闭存卡。 */
    @Bean
    RestClientHttpServiceGroupConfigurer clientMasterClient(InternalTokenClientOptions internalToken) {
        return HttpServiceClientFactory.configureGroup(SERVICE_ID,
            internalToken.apply(HttpServiceClientOptions.strict().withStrictJsonResponses(8192), SERVICE_ID));
    }
}
