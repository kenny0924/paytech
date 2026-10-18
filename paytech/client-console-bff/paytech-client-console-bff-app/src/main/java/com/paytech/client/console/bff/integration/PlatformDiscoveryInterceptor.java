package com.paytech.client.console.bff.integration;

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

/**
 * 在实例选择边界区分发现不可用，避免将框架缺实例的 IllegalStateException 当成程序错误。
 *
 * @author Codex
 */
@Component
@AllArgsConstructor
public final class PlatformDiscoveryInterceptor implements ClientHttpRequestInterceptor {
    private static final String SERVICE_ID = "platform-support-service";
    private final LoadBalancerClient loadBalancer;

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
        throws IOException {
        var instance = loadBalancer.choose(SERVICE_ID);
        if (instance == null) {
            throw new ResourceAccessException("Platform Support 没有可用的发现实例");
        }
        var exchange = new LoadBalancerRequestFactory(loadBalancer).createRequest(request, body, execution);
        return loadBalancer.execute(SERVICE_ID, instance, exchange);
    }
}
