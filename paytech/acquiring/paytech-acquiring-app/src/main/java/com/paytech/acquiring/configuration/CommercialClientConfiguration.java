package com.paytech.acquiring.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

import com.paytech.acquiring.api.client.v1.enums.CommerceCheckoutErrorCode;
import com.paytech.acquiring.domainservice.port.customer.CheckoutCommercialPort;
import com.paytech.acquiring.integration.commercial.impl.CheckoutCommercialPortImpl;
import com.paytech.commercial.api.checkout.CheckoutContextApi;
import com.paytech.commercial.api.enums.CheckoutErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;

/**
 * Checkout 商业上下文真实解析（acquiring.commercial.resolution-mode=REAL）：commercial 解析接口使用服务发现、负载均衡和 Foundation
 * 公共出站边界
 * <li>登记 commercial 结账错误码，使 422 / 409 / 404 的合同 Code 解码为明确失败，由端口按 Client API 合同转换。</li>
 * <li>读超时映射 504 DEPENDENCY_TIMEOUT，其余依赖故障（连接失败、5xx 含 OWNER_UNAVAILABLE）映射 503 DEPENDENCY_UNAVAILABLE；禁止自动重试和重定向，
 * CCT_CONTEXT_CHANGED 的一次重试由端口控制。</li>
 * <li>MOCK（默认）时本配置整体不生效，不注册 commercial 客户端，商业端口由 {@link CommercialMockConfiguration} 提供。</li>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = CommercialMockConfiguration.RESOLUTION_MODE, havingValue = "REAL")
@ImportHttpServices(group = CommercialClientConfiguration.SERVICE_ID, types = CheckoutContextApi.class)
public class CommercialClientConfiguration {
    /** commercial 服务组名，同时是服务发现中的服务 ID。 */
    public static final String SERVICE_ID = "commercial-service";

    /** 响应体上限：解析响应含全部开通项与快照，按 commercial 方案取 256 KiB。 */
    static final int MAX_RESPONSE_BYTES = 262144;

    /** 严格 JSON、登记合同错误码并映射依赖故障；内部 Token 按 internal-auth 模式追加。 */
    @Bean
    RestClientHttpServiceGroupConfigurer commercialClient(InternalTokenClientOptions internalToken) {
        HttpServiceClientOptions options = HttpServiceClientOptions.strict()
            .withStrictJsonResponses(MAX_RESPONSE_BYTES)
            .withDependencyFailureMapper((kind, cause) -> switch (kind) {
                case TIMEOUT -> new ErrorException(CommerceCheckoutErrorCode.DEPENDENCY_TIMEOUT, null, cause);
                case UNAVAILABLE -> new ServiceUnavailableException(CommerceCheckoutErrorCode.DEPENDENCY_UNAVAILABLE, cause);
            });
        return HttpServiceClientFactory.configureGroup(SERVICE_ID, internalToken.apply(options, SERVICE_ID),
            CheckoutErrorCode.values());
    }

    /** 真实解析端口：事务外调用 commercial，按端口说明转换错误。 */
    @Bean
    CheckoutCommercialPort checkoutCommercialPort(CheckoutContextApi checkoutContextApi) {
        return new CheckoutCommercialPortImpl(checkoutContextApi);
    }
}
