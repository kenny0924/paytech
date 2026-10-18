package com.paytech.operations.console.bff.integration;

import java.io.IOException;

import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerRequestFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;

/** 按服务名发现 Owner 实例；缺实例按依赖故障转换且不重试。 */
public final class OwnerDiscoveryInterceptor implements ClientHttpRequestInterceptor {
    private final LoadBalancerClient loadBalancer;
    private final String serviceId;

    /** 指定发现的服务名。 */
    public OwnerDiscoveryInterceptor(LoadBalancerClient loadBalancer, String serviceId) {
        this.loadBalancer = loadBalancer;
        this.serviceId = serviceId;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        var instance = loadBalancer.choose(serviceId);
        if (instance == null) {
            throw new ResourceAccessException(serviceId + " 没有可用的发现实例");
        }
        var exchange = new LoadBalancerRequestFactory(loadBalancer).createRequest(request, body, execution);
        return loadBalancer.execute(serviceId, instance, exchange);
    }
}
