package com.paytech.acquiring.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

import com.paytech.acquiring.api.client.v1.enums.CommerceCheckoutErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;
import com.paytech.tax.api.TaxCalculationApi;
import com.paytech.tax.api.enums.TaxErrorCode;

/**
 * 结账报价的 tax 计算接口：固定私网地址（tax 不注册 Nacos，基址由 ACQUIRING_TAX_SERVICE_URL 配置）和 Foundation 公共出站边界
 * <li>登记 TaxErrorCode，使 409 TAX_CALCULATION_CONFLICT 解码为明确失败，由端口转换为并发冲突。</li>
 * <li>读超时映射 504 DEPENDENCY_TIMEOUT，其余依赖故障映射 503 DEPENDENCY_UNAVAILABLE；禁止自动重试和重定向。</li>
 */
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = TaxClientConfiguration.SERVICE_ID, types = TaxCalculationApi.class)
public class TaxClientConfiguration {
    /** tax HTTP 服务组名。 */
    public static final String SERVICE_ID = "tax-service";

    /** 响应体上限：最多 200 行，每行含组件明细，按 256 KiB。 */
    static final int MAX_RESPONSE_BYTES = 262144;

    /** 严格 JSON、登记合同错误码并映射依赖故障；内部 Token 按 internal-auth 模式追加。 */
    @Bean
    RestClientHttpServiceGroupConfigurer taxClient(InternalTokenClientOptions internalToken) {
        HttpServiceClientOptions options = HttpServiceClientOptions.strict()
            .withStrictJsonResponses(MAX_RESPONSE_BYTES)
            .withDependencyFailureMapper((kind, cause) -> switch (kind) {
                case TIMEOUT -> new ErrorException(CommerceCheckoutErrorCode.DEPENDENCY_TIMEOUT, null, cause);
                case UNAVAILABLE -> new ServiceUnavailableException(CommerceCheckoutErrorCode.DEPENDENCY_UNAVAILABLE, cause);
            });
        return HttpServiceClientFactory.configureGroup(SERVICE_ID, internalToken.apply(options, SERVICE_ID),
            TaxErrorCode.values());
    }
}
