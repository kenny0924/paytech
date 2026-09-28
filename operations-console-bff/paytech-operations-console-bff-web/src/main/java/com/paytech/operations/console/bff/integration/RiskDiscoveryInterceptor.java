package com.paytech.operations.console.bff.integration;

import java.io.IOException;

import lombok.AllArgsConstructor;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerRequestFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

/** Risk 使用独立发现入口，缺实例按依赖故障转换且不重试。 */
@Component
@AllArgsConstructor
public final class RiskDiscoveryInterceptor implements ClientHttpRequestInterceptor {
    private static final String SERVICE_ID = "risk-service";
    private final LoadBalancerClient loadBalancer;

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
        throws IOException {
        var instance = loadBalancer.choose(SERVICE_ID);
        if (instance == null) {
            throw new ResourceAccessException("Risk 没有可用的发现实例");
        }
        var exchange = new LoadBalancerRequestFactory(loadBalancer).createRequest(request, body, execution);
        return loadBalancer.execute(SERVICE_ID, instance, exchange);
    }
}
